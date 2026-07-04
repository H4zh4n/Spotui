package com.music.spotui.ui.notification;

import com.music.spotui.di.CurrentSongState;
import com.music.spotui.ui.repository.AppRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class PlaybackService_MembersInjector implements MembersInjector<PlaybackService> {
  private final Provider<CurrentSongState> currentSongStateProvider;

  private final Provider<AppRepository> repositoryProvider;

  private PlaybackService_MembersInjector(Provider<CurrentSongState> currentSongStateProvider,
      Provider<AppRepository> repositoryProvider) {
    this.currentSongStateProvider = currentSongStateProvider;
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public void injectMembers(PlaybackService instance) {
    injectCurrentSongState(instance, currentSongStateProvider.get());
    injectRepository(instance, repositoryProvider.get());
  }

  public static MembersInjector<PlaybackService> create(
      Provider<CurrentSongState> currentSongStateProvider,
      Provider<AppRepository> repositoryProvider) {
    return new PlaybackService_MembersInjector(currentSongStateProvider, repositoryProvider);
  }

  @InjectedFieldSignature("com.music.spotui.ui.notification.PlaybackService.currentSongState")
  public static void injectCurrentSongState(PlaybackService instance,
      CurrentSongState currentSongState) {
    instance.currentSongState = currentSongState;
  }

  @InjectedFieldSignature("com.music.spotui.ui.notification.PlaybackService.repository")
  public static void injectRepository(PlaybackService instance, AppRepository repository) {
    instance.repository = repository;
  }
}
