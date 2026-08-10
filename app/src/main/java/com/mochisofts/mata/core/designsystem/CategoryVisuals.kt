package com.mochisofts.mata.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIconOption(
    val id: String,
    val label: String,
    val imageVector: ImageVector,
)

val CategoryIconOptions = listOf(
    CategoryIconOption("Category", "カテゴリ", Icons.Outlined.Category),
    CategoryIconOption("Home", "家", Icons.Outlined.Home),
    CategoryIconOption("ShoppingCart", "買い物", Icons.Outlined.ShoppingCart),
    CategoryIconOption("Restaurant", "食事", Icons.Outlined.Restaurant),
    CategoryIconOption("Favorite", "健康", Icons.Outlined.Favorite),
    CategoryIconOption("FitnessCenter", "筋力トレーニング", Icons.Outlined.FitnessCenter),
    CategoryIconOption("DirectionsRun", "ランニング", Icons.Outlined.DirectionsRun),
    CategoryIconOption("School", "学校", Icons.Outlined.School),
    CategoryIconOption("MenuBook", "読書", Icons.Outlined.MenuBook),
    CategoryIconOption("Work", "仕事", Icons.Outlined.Work),
    CategoryIconOption("SportsEsports", "ゲーム", Icons.Outlined.SportsEsports),
    CategoryIconOption("Event", "イベント", Icons.Outlined.Event),
    CategoryIconOption("Pets", "ペット", Icons.Outlined.Pets),
)

fun categoryIcon(id: String): ImageVector =
    CategoryIconOptions.firstOrNull { it.id == id }?.imageVector ?: Icons.Outlined.Category

val CategoryColorNames = listOf(
    "赤", "ピンク", "紫", "藍", "青", "水色", "シアン", "青緑",
    "緑", "黄緑", "ライム", "黄", "オレンジ", "濃いオレンジ", "茶", "グレー",
)
