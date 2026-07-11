package com.music.spotui.ui.navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.ui.screens.SpotifyLoginScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.compose.ui.window.DialogProperties
import com.music.spotui.ui.screens.AlbumScreen
import com.music.spotui.ui.screens.ArtistReleasesScreen
import com.music.spotui.ui.screens.ArtistScreen
import com.music.spotui.ui.screens.CategoryScreen
import com.music.spotui.ui.screens.DownloadsScreen
import com.music.spotui.ui.screens.HistoryScreen
import com.music.spotui.ui.screens.HomeScreen
import com.music.spotui.ui.screens.LibraryScreen
import com.music.spotui.ui.screens.LikedSongsScreen
import com.music.spotui.ui.screens.PlayerScreen
import com.music.spotui.ui.screens.PlaylistScreen
import com.music.spotui.ui.screens.ShowScreen
import com.music.spotui.ui.screens.QueueScreen
import com.music.spotui.ui.screens.SearchScreen
import com.music.spotui.ui.screens.SettingsScreen
import com.music.spotui.ui.viewmodel.PlayerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun MyNavHost(
    navHostController: NavHostController
) {

    val playerViewModel : PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.currentSongTitle

    Log.d("player", playerState.toString())

//    val context = LocalContext.current
//    var player : ExoPlayer? = null
//    player = ExoPlayer.Builder(context).build()

    val context = LocalContext.current
    // First launch (no Spotify session) lands on the login screen.
    val startDestination = if (SpotifySession.spDc(context).isBlank()) {
        Routes.Login.route
    } else {
        Routes.Home.route
    }

    // Restore the last session: put the track back into the mini player (paused)
    // and arm the player to resume from the saved position on the first play tap.
    LaunchedEffect(Unit) {
        if (playerViewModel.currentSongTitle.value.isBlank()) {
            com.music.spotui.data.preferences.loadLastPlayback(context)?.let { (song, positionMs) ->
                playerViewModel.updateQueue(listOf(song))
                playerViewModel.updateSongState(
                    song.coverUri, song.title, song.singer, false, song.id, 0, song.album)
                com.music.spotui.di.SongPlayer.setRestorePoint(song.url, positionMs)
            }
        }
    }

    NavHost(
        navController = navHostController,
        startDestination = startDestination,
        // Quick fade between screens instead of the default slide/scale animations.
        enterTransition = { fadeIn(animationSpec = tween(150)) },
        exitTransition = { fadeOut(animationSpec = tween(150)) },
        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) },
    ){
        composable(Routes.Login.route){
            SpotifyLoginScreen(navHostController)
        }
        composable(Routes.Home.route){
            HomeScreen(navHostController)
        }
        composable(Routes.Search.route){
            SearchScreen(navHostController)
        }
        composable(Routes.Library.route) {
            LibraryScreen(navHostController)
        }
        dialog(
            route = Routes.Player.route,
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            PlayerScreen(navHostController)
        }

        composable(Routes.Queue.route){
            QueueScreen(navHostController)
        }

        composable(Routes.Liked.route){
            LikedSongsScreen(navHostController)
        }

        composable(Routes.Downloads.route){
            DownloadsScreen(navHostController)
        }

        composable(Routes.Settings.route){
            SettingsScreen(navHostController)
        }

        composable(Routes.History.route){
            HistoryScreen(navHostController)
        }

        composable(
            "${Routes.Category.route}/{genre}?title={title}",
            arguments = listOf(navArgument("title") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val genre = navBackStackEntry.arguments?.getString("genre").orEmpty()
            val title = navBackStackEntry.arguments?.getString("title").orEmpty()
            CategoryScreen(navHostController, genre = genre, title = title.ifBlank { genre })
        }

        composable(
            "${Routes.Album.route}/{uString}?artist={artist}",
            arguments = listOf(navArgument("artist") { defaultValue = "" }),
        ) { navBackStackEntry ->
            /* Extracting the id from the route */
            val uId = navBackStackEntry.arguments?.getString("uString")
            val artist = navBackStackEntry.arguments?.getString("artist").orEmpty()
            /* We check if it's not null */
            uId?.let { id->
                AlbumScreen(navController = navHostController, albumName = id, artist = artist)
            }
        }

        composable(
            "${Routes.Playlist.route}/{pId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val pId = navBackStackEntry.arguments?.getString("pId")
            val name = navBackStackEntry.arguments?.getString("name").orEmpty()
            pId?.let { PlaylistScreen(navHostController, playlistId = it, playlistName = name) }
        }

        composable(
            "${Routes.Show.route}/{sId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val sId = navBackStackEntry.arguments?.getString("sId")
            val name = navBackStackEntry.arguments?.getString("name").orEmpty()
            sId?.let { ShowScreen(navHostController, showId = it, showName = name) }
        }

        composable("${Routes.ArtistReleases.route}/{aString}") { navBackStackEntry ->
            val aId = navBackStackEntry.arguments?.getString("aString")
            aId?.let { ArtistReleasesScreen(navHostController, it) }
        }

        composable(
            "${Routes.Artist.route}/{aString}?id={artistId}",
            arguments = listOf(navArgument("artistId") { defaultValue = "" }),
        ) { navBackStackEntry ->
            /* Extracting the id from the route */
            val aId = navBackStackEntry.arguments?.getString("aString")
            val artistId = navBackStackEntry.arguments?.getString("artistId").orEmpty()
            /* We check if it's not null */
            aId?.let { aid->
                ArtistScreen(navHostController, aid, artistId)
            }
        }
    }
}
