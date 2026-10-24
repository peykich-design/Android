package com.peykich.sofapanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peykich.sofapanel.domain.TimeRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 待機画面：大きな時計・日付・温湿度・最後に実行したシーン名だけ。どこをタップしてもメインに戻る */
@Composable
fun StandbyScreen(vm: PanelViewModel) {
    val now by vm.now.collectAsState()
    val climate by vm.repo.climate.collectAsState()
    val lastRun by vm.repo.lastRunName.collectAsState()
    // 焼き付き対策：数分ごとに数ピクセルずらす
    val (dx, dy) = TimeRules.burnInShift(now)
    Box(Modifier.fillMaxSize().background(PanelColors.StandbyBg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.offset(dx.dp, dy.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                SimpleDateFormat("H:mm", Locale.JAPAN).format(Date(now)),
                color = PanelColors.Text.copy(alpha = 0.85f),
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Thin,
                fontSize = 128.sp,
                lineHeight = 132.sp,
            )
            Text(
                SimpleDateFormat("M月d日（E）", Locale.JAPAN).format(Date(now)),
                color = PanelColors.SubText,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            ClimateView(climate, 20)
            lastRun?.let {
                Spacer(Modifier.height(12.dp))
                Text("最後の実行：$it", color = PanelColors.SubText, fontSize = 15.sp)
            }
        }
    }
}

/** 夜間の画面オフ時間帯：真っ黒。タップで待機画面を点灯 */
@Composable
fun NightScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black))
}
