package com.music.spotui

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.music.spotui.ui.navigation.MainBottomNavigation
import com.music.spotui.ui.navigation.MyNavHost
import com.music.spotui.ui.navigation.Routes
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay


@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun App() {
    val bottomBarState = rememberSaveable { (mutableStateOf(true)) }
    val bottomBarPlayerState = rememberSaveable { (mutableStateOf(true)) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playerViewModel: com.music.spotui.ui.viewmodel.PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.currentSongTitle
    var lastRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentRoute, playerState) {
        if (currentRoute != Routes.Player.route) {
            bottomBarState.value = when (currentRoute) {
                Routes.Login.route, Routes.Queue.route -> false
                else -> true
            }
            bottomBarPlayerState.value = when (currentRoute) {
                Routes.Login.route, Routes.Queue.route -> false
                else -> playerState.isNotEmpty()
            }
        }
        lastRoute = currentRoute
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(navController) {
        // Process any cold-start deep link intent that launched the app
        com.music.spotui.util.DeepLinkHandler.consumePendingUri()?.let { uri ->
            com.music.spotui.util.DeepLinkHandler.processUri(uri, context, navController, playerViewModel, scope)
        }
        // Process any new deep link intent while the app is active
        com.music.spotui.util.DeepLinkHandler.deepLinkFlow.collect { uri ->
            com.music.spotui.util.DeepLinkHandler.processUri(uri, context, navController, playerViewModel, scope)
        }
    }

    Scaffold(
        modifier = Modifier
            .navigationBarsPadding()
        ,
        bottomBar = {
            MainBottomNavigation(navController = navController, bottomBarState = bottomBarState, bottomBarPlayerState)
        }
    ) {
        MyNavHost(navHostController = navController)
    }
}


