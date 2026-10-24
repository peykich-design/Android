package com.peykich.sofapanel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.peykich.sofapanel.service.PanelService
import com.peykich.sofapanel.ui.IdleMode
import com.peykich.sofapanel.ui.PanelRoot
import com.peykich.sofapanel.ui.PanelViewModel

class MainActivity : ComponentActivity() {
    private val vm: PanelViewModel by viewModels()

    /** 待機画面から戻すためのタップを、指を離すまで画面に渡さない */
    private var swallowing = false

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // アプリ表示中は画面を消さない
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val idle = vm.idle
            // 待機・夜間はアプリ内で輝度を最低にし、メインに戻ったら元に戻す
            LaunchedEffect(idle) { setDim(idle != IdleMode.ACTIVE) }
            PanelRoot(vm)
        }
    }

    override fun onStart() {
        super.onStart()
        PanelService.start(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            swallowing = vm.onUserInteraction()
        } else if (ev.actionMasked == MotionEvent.ACTION_MOVE && !swallowing) {
            vm.onUserInteraction()
        }
        if (swallowing) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) swallowing = false
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setDim(dim: Boolean) {
        val lp = window.attributes
        lp.screenBrightness = if (dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    /** システムバーとナビゲーションバーを隠す（端からスワイプで一時的に出る） */
    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
