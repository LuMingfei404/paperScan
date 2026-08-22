package com.papersnap.app

import android.os.Bundle
import android.app.Activity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papersnap.app.ui.BookmarksScreen
import com.papersnap.app.ui.DetailScreen
import com.papersnap.app.ui.FeedScreen
import com.papersnap.app.ui.FilterScreen
import com.papersnap.app.ui.HistoryScreen
import com.papersnap.app.ui.SettingsScreen
import com.papersnap.app.ui.theme.PaperSnapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val dark by vm.darkTheme.collectAsState()
            PaperSnapTheme(darkTheme = dark) {
                val screen by vm.screen.collectAsState()
                val context = LocalContext.current
                val activity = context as? Activity

                // 系统返回键：详情/设置等返回上一级，首页按返回退出 App
                BackHandler {
                    if (screen != Screen.Feed) vm.back() else activity?.finish()
                }

                LaunchedEffect(Unit) {
                    vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                }

                when (val s = screen) {
                    Screen.Feed -> FeedScreen(vm)
                    is Screen.Detail -> DetailScreen(vm, s.id)
                    Screen.Filter -> FilterScreen(vm)
                    Screen.Bookmarks -> BookmarksScreen(vm)
                    Screen.History -> HistoryScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}
