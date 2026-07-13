package com.music.spotui.data.recommendation

import android.util.Log
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyArtist
import com.metrolist.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Personalized recommendation engine, ported from Meld's SpotifyRecommendationEngine.
 *
 * Spotify deprecated its `/v1/recommendations` and related-artists endpoints for
 * cookie/dev-mode tokens, so this builds its own recommender from endpoints that
 * still work:
 * 1. **Taste profile** — the user's top tracks/artists → artist-affinity + genre maps.
 * 2. **Candidate generation** — seed-artist top tracks, same-album tracks,
 *    genre-neighbour artist top tracks, and the user's top-track pool.
 * 3. **Composite scoring** — source relevance, artist affinity, genre overlap,
 *    popularity similarity, recency.
 * 4. **Diversification** — per-artist cap + bucket interleaving.
 */
object SpotifyRecommendationEngine {

    private const val TAG = "SpotifyRecEngine"
    private const val PROFILE_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours
    private const val MAX_TRACKS_PER_ARTIST = 3

    // Scoring weights
    private const val W_SOURCE = 0.25f
    private const val W_AFFINITY = 0.30f
    private const val W_GENRE = 0.20f
    private const val W_POPULARITY = 0.10f
    private const val W_RECENCY = 0.15f

    @Volatile private var artistAffinityMap: Map<String, Float> = emptyMap()
    @Volatile private var artistGenreMap: Map<String, Set<String>> = emptyMap()
    @Volatile private var topTrackPool: List<SpotifyTrack> = emptyList()
    @Volatile private var shortTermArtistIds: Set<String> = emptySet()
    @Volatile private var lastProfileRefresh: Long = 0L
    @Volatile private var lastProfileFailure: Long = 0L

    private enum class Bucket(val sourceScore: Float) {
        SEED_ARTIST(1.0f),
        SAME_ALBUM(0.85f),
        GENRE_NEIGHBOR(0.65f),
        USER_TOP(0.45f),
    }

    private data class ScoredCandidate(
        val track: SpotifyTrack,
        val bucket: Bucket,
        val sourceScore: Float,
        val artistAffinity: Float,
        val genreOverlap: Float,
        val popularitySimilarity: Float,
        val recencyBoost: Float,
    ) {
        val finalScore: Float
            get() = (W_SOURCE * sourceScore) +
                (W_AFFINITY * artistAffinity) +
                (W_GENRE * genreOverlap) +
                (W_POPULARITY * popularitySimilarity) +
                (W_RECENCY * recencyBoost)
    }

    /** Builds/refreshes the user's taste profile from their Spotify top tracks/artists. */
    private suspend fun ensureProfileLoaded(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastProfileFailure < 15 * 60 * 1000) { // 15 minutes cooldown on failure
            return@withContext artistAffinityMap.isNotEmpty()
        }
        if (now - lastProfileRefresh < PROFILE_TTL_MS && artistAffinityMap.isNotEmpty()) {
            return@withContext true
        }

        try {
            val profileTracks: List<SpotifyTrack> =
                Spotify.topTracks("medium_term", limit = 50).getOrNull()?.items ?: emptyList()
            val profileArtists: List<SpotifyArtist> =
                Spotify.topArtists("medium_term", limit = 50).getOrNull()?.items ?: emptyList()

            if (profileTracks.isEmpty() && profileArtists.isEmpty()) {
                Log.w(TAG, "No profile data available")
                lastProfileFailure = System.currentTimeMillis()
                return@withContext false
            }

            val affinityBuilder = mutableMapOf<String, Float>()
            val genreBuilder = mutableMapOf<String, MutableSet<String>>()

            for ((index, artist) in profileArtists.withIndex()) {
                if (artist.id.isEmpty()) continue
                val positionScore = 1.0f - (index.toFloat() / profileArtists.size.coerceAtLeast(1))
                affinityBuilder[artist.id] = (affinityBuilder[artist.id] ?: 0f) + (positionScore * 2.0f)
                if (artist.genres.isNotEmpty()) {
                    genreBuilder.getOrPut(artist.id) { mutableSetOf() }.addAll(artist.genres)
                }
            }
            for ((index, track) in profileTracks.withIndex()) {
                val positionScore = 1.0f - (index.toFloat() / profileTracks.size.coerceAtLeast(1))
                for (artist in track.artists) {
                    val artistId = artist.id ?: continue
                    affinityBuilder[artistId] = (affinityBuilder[artistId] ?: 0f) + (positionScore * 1.0f)
                }
            }

            val maxAffinity = affinityBuilder.values.maxOrNull() ?: 1f
            val normalizedAffinity =
                if (maxAffinity > 0f) affinityBuilder.mapValues { it.value / maxAffinity } else affinityBuilder

            val seenTrackIds = mutableSetOf<String>()
            val trackPool = profileTracks.filter { it.id.isNotEmpty() && seenTrackIds.add(it.id) }

            artistAffinityMap = normalizedAffinity
            artistGenreMap = genreBuilder.mapValues { it.value.toSet() }
            topTrackPool = trackPool
            shortTermArtistIds = profileArtists.take(10).map { it.id }.filter { it.isNotEmpty() }.toSet()
            lastProfileRefresh = System.currentTimeMillis()

            Log.d(TAG, "Profile built — ${normalizedAffinity.size} artists, ${trackPool.size} tracks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build profile", e)
            lastProfileFailure = System.currentTimeMillis()
            false
        }
    }

    /** Generate a personalized list of recommended tracks seeded by [seedTrack]. */
    suspend fun getRecommendations(seedTrack: SpotifyTrack, limit: Int = 25): List<SpotifyTrack> =
        withContext(Dispatchers.IO) {
            if (!ensureProfileLoaded()) {
                Log.w(TAG, "Profile unavailable")
                return@withContext emptyList()
            }

            val candidates = mutableListOf<ScoredCandidate>()
            val seenIds = mutableSetOf(seedTrack.id)
            val seedArtistIds = seedTrack.artists.mapNotNull { it.id }.toSet()
            val seedPopularity = seedTrack.popularity ?: 50
            val seedGenres = seedArtistIds.flatMap { artistGenreMap[it].orEmpty() }.toSet()

            // Source 1: seed-artist top tracks
            coroutineScope {
                seedArtistIds.take(2).map { artistId ->
                    async {
                        Spotify.artistTopTracks(artistId).getOrNull()?.tracks?.let { tracks ->
                            synchronized(candidates) {
                                for (track in tracks) {
                                    if (track.id.isNotEmpty() && seenIds.add(track.id)) {
                                        candidates.add(buildCandidate(track, Bucket.SEED_ARTIST, seedPopularity, seedGenres))
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            // Source 2: same-album tracks
            seedTrack.album?.id?.let { albumId ->
                Spotify.album(albumId).getOrNull()?.tracks?.items?.let { albumTracks ->
                    for (track in albumTracks) {
                        if (track.id.isNotEmpty() && seenIds.add(track.id)) {
                            candidates.add(buildCandidate(track, Bucket.SAME_ALBUM, seedPopularity, seedGenres))
                        }
                    }
                }
            }

            // Source 3: genre-neighbour artist top tracks
            coroutineScope {
                findGenreNeighbors(seedArtistIds, seedGenres).take(4).map { artistId ->
                    async {
                        Spotify.artistTopTracks(artistId).getOrNull()?.tracks?.let { tracks ->
                            synchronized(candidates) {
                                for (track in tracks) {
                                    if (track.id.isNotEmpty() && seenIds.add(track.id)) {
                                        candidates.add(buildCandidate(track, Bucket.GENRE_NEIGHBOR, seedPopularity, seedGenres))
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            // Source 4: user's top-track pool
            for (track in topTrackPool) {
                if (track.id.isNotEmpty() && seenIds.add(track.id)) {
                    candidates.add(buildCandidate(track, Bucket.USER_TOP, seedPopularity, seedGenres))
                }
            }

            diversify(candidates.sortedByDescending { it.finalScore }, limit)
        }

    private fun buildCandidate(
        track: SpotifyTrack,
        bucket: Bucket,
        seedPopularity: Int,
        seedGenres: Set<String>,
    ): ScoredCandidate {
        val trackArtistIds = track.artists.mapNotNull { it.id }
        val affinity = trackArtistIds.maxOfOrNull { artistAffinityMap[it] ?: 0f } ?: 0f
        val trackGenres = trackArtistIds.flatMap { artistGenreMap[it].orEmpty() }.toSet()
        val genreOverlap = if (seedGenres.isNotEmpty() && trackGenres.isNotEmpty()) {
            seedGenres.intersect(trackGenres).size.toFloat() / seedGenres.union(trackGenres).size.toFloat()
        } else 0f
        val popDiff = abs((track.popularity ?: 50) - seedPopularity)
        val popSimilarity = 1.0f - (popDiff.toFloat() / 100f)
        val recency = if (trackArtistIds.any { it in shortTermArtistIds }) 1.0f else 0f
        return ScoredCandidate(track, bucket, bucket.sourceScore, affinity, genreOverlap, popSimilarity, recency)
    }

    /** Approximates related-artists by finding profile artists that share genres with the seed. */
    private fun findGenreNeighbors(seedArtistIds: Set<String>, seedGenres: Set<String>): List<String> {
        if (seedGenres.isEmpty()) {
            return artistAffinityMap.filterKeys { it !in seedArtistIds }
                .entries.sortedByDescending { it.value }.take(5).map { it.key }
        }
        return artistGenreMap.filterKeys { it !in seedArtistIds }
            .map { (artistId, genres) ->
                val intersection = seedGenres.intersect(genres).size
                val union = seedGenres.union(genres).size
                val jaccard = if (union > 0) intersection.toFloat() / union.toFloat() else 0f
                val affinity = artistAffinityMap[artistId] ?: 0f
                artistId to (jaccard * 0.6f + affinity * 0.4f)
            }
            .filter { it.second > 0.05f }
            .sortedByDescending { it.second }
            .take(6)
            .map { it.first }
    }

    private fun diversify(ranked: List<ScoredCandidate>, limit: Int): List<SpotifyTrack> {
        val result = mutableListOf<SpotifyTrack>()
        val artistCount = mutableMapOf<String, Int>()
        val lastBuckets = mutableListOf<Bucket>()
        val usedIndices = mutableSetOf<Int>()

        while (result.size < limit && usedIndices.size < ranked.size) {
            val preferDifferentBucket = lastBuckets.size >= 3 &&
                lastBuckets.takeLast(3).distinct().size == 1

            var bestIndex = -1
            for (i in ranked.indices) {
                if (i in usedIndices) continue
                val candidate = ranked[i]
                val mainArtist = candidate.track.artists.firstOrNull()?.id ?: ""
                if ((artistCount[mainArtist] ?: 0) >= MAX_TRACKS_PER_ARTIST) continue
                if (preferDifferentBucket && lastBuckets.isNotEmpty()) {
                    if (candidate.bucket != lastBuckets.last()) { bestIndex = i; break }
                    if (bestIndex == -1) bestIndex = i
                } else { bestIndex = i; break }
            }
            if (bestIndex == -1) break

            val chosen = ranked[bestIndex]
            usedIndices.add(bestIndex)
            result.add(chosen.track)
            val mainArtist = chosen.track.artists.firstOrNull()?.id ?: ""
            artistCount[mainArtist] = (artistCount[mainArtist] ?: 0) + 1
            lastBuckets.add(chosen.bucket)
            if (lastBuckets.size > 5) lastBuckets.removeAt(0)
        }
        return result
    }

    fun invalidateProfile() {
        lastProfileRefresh = 0L
        artistAffinityMap = emptyMap()
        artistGenreMap = emptyMap()
        topTrackPool = emptyList()
        shortTermArtistIds = emptySet()
    }
}
