package com.mochisofts.mata.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mochisofts.mata.R

data class CategoryColorOption(
    val id: String,
    @StringRes val labelRes: Int,
    val baseArgb: Long,
)

/** Stable persisted order for the fixed 16-color category palette. */
val CategoryColorOptions = listOf(
    CategoryColorOption("red", R.string.category_color_red, 0xFFC62828),
    CategoryColorOption("pink", R.string.category_color_pink, 0xFFAD1457),
    CategoryColorOption("purple", R.string.category_color_purple, 0xFF6A1B9A),
    CategoryColorOption("indigo", R.string.category_color_indigo, 0xFF283593),
    CategoryColorOption("blue", R.string.category_color_blue, 0xFF1565C0),
    CategoryColorOption("light_blue", R.string.category_color_light_blue, 0xFF0277BD),
    CategoryColorOption("cyan", R.string.category_color_cyan, 0xFF00838F),
    CategoryColorOption("teal", R.string.category_color_teal, 0xFF00796B),
    CategoryColorOption("green", R.string.category_color_green, 0xFF2E7D32),
    CategoryColorOption("light_green", R.string.category_color_light_green, 0xFF558B2F),
    CategoryColorOption("lime", R.string.category_color_lime, 0xFF827717),
    CategoryColorOption("yellow", R.string.category_color_yellow, 0xFFF9A825),
    CategoryColorOption("orange", R.string.category_color_orange, 0xFFEF6C00),
    CategoryColorOption("deep_orange", R.string.category_color_deep_orange, 0xFFD84315),
    CategoryColorOption("brown", R.string.category_color_brown, 0xFF5D4037),
    CategoryColorOption("gray", R.string.category_color_gray, 0xFF546E7A),
)

val CategoryColorNameResIds = CategoryColorOptions.map(CategoryColorOption::labelRes)

data class CategoryIconOption(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val groupRes: Int,
    @StringRes val keywordsRes: Int,
    val imageVector: ImageVector,
)

/** Material Icons catalog whose IDs are persisted by category records. */
val CategoryIconOptions = listOf(
    categoryIconOption("Home", R.string.category_icon_home, R.string.category_icon_group_life, R.string.category_icon_keywords_home, Icons.Outlined.Home),
    categoryIconOption("Bed", R.string.category_icon_bed, R.string.category_icon_group_life, R.string.category_icon_keywords_bed, Icons.Outlined.Bed),
    categoryIconOption("WbSunny", R.string.category_icon_sunny, R.string.category_icon_group_life, R.string.category_icon_keywords_sunny, Icons.Outlined.WbSunny),
    categoryIconOption("CleaningServices", R.string.category_icon_cleaning, R.string.category_icon_group_housework, R.string.category_icon_keywords_cleaning, Icons.Outlined.CleaningServices),
    categoryIconOption("LocalLaundryService", R.string.category_icon_laundry, R.string.category_icon_group_housework, R.string.category_icon_keywords_laundry, Icons.Outlined.LocalLaundryService),
    categoryIconOption("DeleteSweep", R.string.category_icon_declutter, R.string.category_icon_group_housework, R.string.category_icon_keywords_declutter, Icons.Outlined.DeleteSweep),
    categoryIconOption("ShoppingCart", R.string.category_icon_shopping, R.string.category_icon_group_shopping, R.string.category_icon_keywords_shopping_cart, Icons.Outlined.ShoppingCart),
    categoryIconOption("ShoppingBag", R.string.category_icon_shopping_bag, R.string.category_icon_group_shopping, R.string.category_icon_keywords_shopping_bag, Icons.Outlined.ShoppingBag),
    categoryIconOption("Storefront", R.string.category_icon_storefront, R.string.category_icon_group_shopping, R.string.category_icon_keywords_storefront, Icons.Outlined.Storefront),
    categoryIconOption("Restaurant", R.string.category_icon_restaurant, R.string.category_icon_group_cooking, R.string.category_icon_keywords_restaurant, Icons.Outlined.Restaurant),
    categoryIconOption("Kitchen", R.string.category_icon_kitchen, R.string.category_icon_group_cooking, R.string.category_icon_keywords_kitchen, Icons.Outlined.Kitchen),
    categoryIconOption("LocalDining", R.string.category_icon_cooking, R.string.category_icon_group_cooking, R.string.category_icon_keywords_cooking, Icons.Outlined.LocalDining),
    categoryIconOption("Favorite", R.string.category_icon_health, R.string.category_icon_group_health, R.string.category_icon_keywords_health, Icons.Outlined.Favorite),
    categoryIconOption("HealthAndSafety", R.string.category_icon_health_management, R.string.category_icon_group_health, R.string.category_icon_keywords_health_management, Icons.Outlined.HealthAndSafety),
    categoryIconOption("SelfImprovement", R.string.category_icon_relax, R.string.category_icon_group_health, R.string.category_icon_keywords_relax, Icons.Outlined.SelfImprovement),
    categoryIconOption("FitnessCenter", R.string.category_icon_fitness, R.string.category_icon_group_exercise, R.string.category_icon_keywords_fitness, Icons.Outlined.FitnessCenter),
    categoryIconOption("DirectionsRun", R.string.category_icon_running, R.string.category_icon_group_exercise, R.string.category_icon_keywords_running, Icons.AutoMirrored.Outlined.DirectionsRun),
    categoryIconOption("SportsSoccer", R.string.category_icon_ball_sports, R.string.category_icon_group_exercise, R.string.category_icon_keywords_ball_sports, Icons.Outlined.SportsSoccer),
    categoryIconOption("Medication", R.string.category_icon_medicine, R.string.category_icon_group_medical, R.string.category_icon_keywords_medicine, Icons.Outlined.Medication),
    categoryIconOption("MedicalServices", R.string.category_icon_hospital, R.string.category_icon_group_medical, R.string.category_icon_keywords_hospital, Icons.Outlined.MedicalServices),
    categoryIconOption("Vaccines", R.string.category_icon_vaccines, R.string.category_icon_group_medical, R.string.category_icon_keywords_vaccines, Icons.Outlined.Vaccines),
    categoryIconOption("School", R.string.category_icon_school, R.string.category_icon_group_study, R.string.category_icon_keywords_school, Icons.Outlined.School),
    categoryIconOption("MenuBook", R.string.category_icon_reading, R.string.category_icon_group_study, R.string.category_icon_keywords_reading, Icons.AutoMirrored.Outlined.MenuBook),
    categoryIconOption("EditNote", R.string.category_icon_note, R.string.category_icon_group_study, R.string.category_icon_keywords_note, Icons.Outlined.EditNote),
    categoryIconOption("Work", R.string.category_icon_work, R.string.category_icon_group_work, R.string.category_icon_keywords_work, Icons.Outlined.Work),
    categoryIconOption("BusinessCenter", R.string.category_icon_business, R.string.category_icon_group_work, R.string.category_icon_keywords_business, Icons.Outlined.BusinessCenter),
    categoryIconOption("Laptop", R.string.category_icon_laptop, R.string.category_icon_group_work, R.string.category_icon_keywords_laptop, Icons.Outlined.Laptop),
    categoryIconOption("SportsEsports", R.string.category_icon_game, R.string.category_icon_group_game, R.string.category_icon_keywords_game, Icons.Outlined.SportsEsports),
    categoryIconOption("Casino", R.string.category_icon_board_game, R.string.category_icon_group_game, R.string.category_icon_keywords_board_game, Icons.Outlined.Casino),
    categoryIconOption("EmojiEvents", R.string.category_icon_achievement, R.string.category_icon_group_game, R.string.category_icon_keywords_achievement, Icons.Outlined.EmojiEvents),
    categoryIconOption("Event", R.string.category_icon_event, R.string.category_icon_group_event, R.string.category_icon_keywords_event, Icons.Outlined.Event),
    categoryIconOption("Celebration", R.string.category_icon_celebration, R.string.category_icon_group_event, R.string.category_icon_keywords_celebration, Icons.Outlined.Celebration),
    categoryIconOption("Flag", R.string.category_icon_goal, R.string.category_icon_group_event, R.string.category_icon_keywords_goal, Icons.Outlined.Flag),
    categoryIconOption("Payments", R.string.category_icon_payment, R.string.category_icon_group_money, R.string.category_icon_keywords_payment, Icons.Outlined.Payments),
    categoryIconOption("Savings", R.string.category_icon_savings, R.string.category_icon_group_money, R.string.category_icon_keywords_savings, Icons.Outlined.Savings),
    categoryIconOption("AccountBalanceWallet", R.string.category_icon_wallet, R.string.category_icon_group_money, R.string.category_icon_keywords_wallet, Icons.Outlined.AccountBalanceWallet),
    categoryIconOption("DirectionsCar", R.string.category_icon_car, R.string.category_icon_group_transport, R.string.category_icon_keywords_car, Icons.Outlined.DirectionsCar),
    categoryIconOption("Train", R.string.category_icon_train, R.string.category_icon_group_transport, R.string.category_icon_keywords_train, Icons.Outlined.Train),
    categoryIconOption("Flight", R.string.category_icon_flight, R.string.category_icon_group_transport, R.string.category_icon_keywords_flight, Icons.Outlined.Flight),
    categoryIconOption("Person", R.string.category_icon_person, R.string.category_icon_group_people, R.string.category_icon_keywords_person, Icons.Outlined.Person),
    categoryIconOption("Groups", R.string.category_icon_groups, R.string.category_icon_group_people, R.string.category_icon_keywords_groups, Icons.Outlined.Groups),
    categoryIconOption("FamilyRestroom", R.string.category_icon_family, R.string.category_icon_group_people, R.string.category_icon_keywords_family, Icons.Outlined.FamilyRestroom),
    categoryIconOption("Pets", R.string.category_icon_pets, R.string.category_icon_group_pets, R.string.category_icon_keywords_pets, Icons.Outlined.Pets),
    categoryIconOption("Category", R.string.category_icon_category, R.string.category_icon_group_other, R.string.category_icon_keywords_category, Icons.Outlined.Category),
    categoryIconOption("Star", R.string.category_icon_star, R.string.category_icon_group_other, R.string.category_icon_keywords_star, Icons.Outlined.Star),
    categoryIconOption("CheckCircle", R.string.category_icon_check, R.string.category_icon_group_other, R.string.category_icon_keywords_check, Icons.Outlined.CheckCircle),
    categoryIconOption("MoreHoriz", R.string.category_icon_more, R.string.category_icon_group_other, R.string.category_icon_keywords_more, Icons.Outlined.MoreHoriz),
)

private fun categoryIconOption(
    id: String,
    @StringRes labelRes: Int,
    @StringRes groupRes: Int,
    @StringRes keywordsRes: Int,
    imageVector: ImageVector,
) = CategoryIconOption(id, labelRes, groupRes, keywordsRes, imageVector)

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
