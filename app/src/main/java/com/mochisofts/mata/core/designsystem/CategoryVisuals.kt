package com.mochisofts.mata.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Work
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.mochisofts.mata.R

data class CategoryIconOption(
    val id: String,
    @StringRes val labelRes: Int,
    val imageVector: ImageVector,
)

val CategoryIconOptions = listOf(
    CategoryIconOption("Category", R.string.category_icon_category, Icons.Outlined.Category),
    CategoryIconOption("Home", R.string.category_icon_home, Icons.Outlined.Home),
    CategoryIconOption("ShoppingCart", R.string.category_icon_shopping, Icons.Outlined.ShoppingCart),
    CategoryIconOption("Restaurant", R.string.category_icon_restaurant, Icons.Outlined.Restaurant),
    CategoryIconOption("Favorite", R.string.category_icon_health, Icons.Outlined.Favorite),
    CategoryIconOption("FitnessCenter", R.string.category_icon_fitness, Icons.Outlined.FitnessCenter),
    CategoryIconOption(
        "DirectionsRun",
        R.string.category_icon_running,
        Icons.AutoMirrored.Outlined.DirectionsRun,
    ),
    CategoryIconOption("School", R.string.category_icon_school, Icons.Outlined.School),
    CategoryIconOption("MenuBook", R.string.category_icon_reading, Icons.AutoMirrored.Outlined.MenuBook),
    CategoryIconOption("Work", R.string.category_icon_work, Icons.Outlined.Work),
    CategoryIconOption("SportsEsports", R.string.category_icon_game, Icons.Outlined.SportsEsports),
    CategoryIconOption("Event", R.string.category_icon_event, Icons.Outlined.Event),
    CategoryIconOption("Pets", R.string.category_icon_pets, Icons.Outlined.Pets),
)

fun categoryIcon(id: String): ImageVector =
    if (id == "CategoryOff") {
        Icons.Outlined.Block
    } else {
        CategoryIconOptions.firstOrNull { it.id == id }?.imageVector ?: Icons.Outlined.Category
    }

@Composable
@ReadOnlyComposable
fun mataCategoryColor(index: Int?): Color {
    val colors = MaterialTheme.mataColors.categoryColors
    return colors.getOrElse(index ?: colors.lastIndex) { colors.last() }
}

val CategoryColorNameResIds = listOf(
    R.string.category_color_red,
    R.string.category_color_pink,
    R.string.category_color_purple,
    R.string.category_color_indigo,
    R.string.category_color_blue,
    R.string.category_color_light_blue,
    R.string.category_color_cyan,
    R.string.category_color_teal,
    R.string.category_color_green,
    R.string.category_color_light_green,
    R.string.category_color_lime,
    R.string.category_color_yellow,
    R.string.category_color_orange,
    R.string.category_color_deep_orange,
    R.string.category_color_brown,
    R.string.category_color_gray,
)
