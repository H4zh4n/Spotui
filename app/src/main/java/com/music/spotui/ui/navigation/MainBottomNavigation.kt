package com.music.spotui.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.music.spotui.ui.components.MiniPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class NoRippleInteractionSource : MutableInteractionSource {

    override val interactions: Flow<Interaction> = emptyFlow()

    override suspend fun emit(interaction: Interaction) {}

    override fun tryEmit(interaction: Interaction) = true
}

@Composable
fun MainBottomNavigation(navController: NavHostController, bottomBarState: MutableState<Boolean>, bottomBarPlayerState : MutableState<Boolean>) {

    val navItems = listOf(
        Routes.Home,
        Routes.Search,
        Routes.Library
    )
    AnimatedVisibility(
        visible = bottomBarState.value,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        content = {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black,
                                Color.Black
                            ),
                            startY = 0f
                        )
                    )
            ) {

                Column(
                    modifier = Modifier.navigationBarsPadding()
                ) {

                    AnimatedVisibility(
                        visible = bottomBarPlayerState.value,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        content = {
                            MiniPlayer(navController)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    NavigationBar(
                        modifier = Modifier
                            .padding(30.dp, 0.dp)
                            .fillMaxWidth(),
                        containerColor = Color.Transparent,
                        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                    ) {
                        val navStack by navController.currentBackStackEntryAsState()
                        val currentRoute = navStack?.destination?.route

                        val rootRoutes = listOf(Routes.Home.route, Routes.Search.route, Routes.Library.route)
                        var currentTab by rememberSaveable { mutableStateOf(Routes.Home.route) }
                        if (currentRoute in rootRoutes) {
                            currentTab = currentRoute!!
                        }

                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentTab == item.route,
                                icon = {
                                    Icon(
                                        painter = painterResource(
                                            id = item.icon
                                        ), contentDescription = "home"
                                    )
                                },
                                label = {
                                    if (currentTab == item.route) {
                                        Text(color = Color.White, text = item.label, fontSize = 11.sp)
                                    } else {
                                        Text(
                                            color = Color.Gray,
                                            text = item.label,
                                            fontSize = 11.sp
                                        )
                                    }
                                },
                                onClick = {
                                    if (currentTab != item.route) {
                                        navController.navigate(item.route) {
                                            navController.graph.startDestinationRoute?.let { startRoute ->
                                                popUpTo(startRoute) {
                                                    saveState = true
                                                }
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else if (currentRoute != item.route) {
                                        navController.popBackStack(item.route, inclusive = false)
                                    }
                                },
                                alwaysShowLabel = true,
                                interactionSource = NoRippleInteractionSource(),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    unselectedIconColor = Color.Gray,
                                    indicatorColor = Color.Transparent
                                )
                            )

                        }
                    }



                }



            }
        }
    )
}
