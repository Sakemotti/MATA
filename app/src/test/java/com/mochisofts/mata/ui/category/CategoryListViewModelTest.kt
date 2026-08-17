package com.mochisofts.mata.ui.category

import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.repository.CategoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun dragOrder_isSavedAndReportsNewPosition() = runTest {
        val repository = FakeCategoryRepository(categories())
        val viewModel = CategoryListViewModel(repository)
        runCurrent()

        assertTrue(viewModel.startReordering())
        assertTrue(viewModel.moveReorderingCategory("a", "c"))
        assertEquals(listOf("b", "c", "a"), viewModel.uiState.value.categories.map(Category::id))

        viewModel.finishReordering("a")
        runCurrent()

        assertEquals(listOf("b", "c", "a"), repository.lastOrderedIds)
        assertFalse(viewModel.uiState.value.isOrderSaving)
        assertEquals(CategoryListEffect.OrderSaved(position = 3, total = 3), viewModel.effects.first())
    }

    @Test
    fun accessibilityMove_savesOneStepAndBlocksAnotherMoveWhileSaving() = runTest {
        val repository = FakeCategoryRepository(categories()).apply {
            reorderGate = CompletableDeferred()
        }
        val viewModel = CategoryListViewModel(repository)
        runCurrent()

        assertTrue(viewModel.moveCategoryOneStep("b", -1))
        assertFalse(viewModel.moveCategoryOneStep("b", 1))
        repository.reorderGate?.complete(Unit)
        runCurrent()

        assertEquals(listOf("b", "a", "c"), repository.lastOrderedIds)
        assertEquals(CategoryListEffect.OrderSaved(position = 1, total = 3), viewModel.effects.first())
    }

    @Test
    fun failedSave_restoresLastPersistedOrderAndReportsError() = runTest {
        val repository = FakeCategoryRepository(categories()).apply {
            reorderResult = Result.failure(IllegalStateException("write failed"))
        }
        val viewModel = CategoryListViewModel(repository)
        runCurrent()

        viewModel.startReordering()
        viewModel.moveReorderingCategory("c", "a")
        viewModel.finishReordering("c")
        runCurrent()

        assertEquals(listOf("a", "b", "c"), viewModel.uiState.value.categories.map(Category::id))
        assertFalse(viewModel.uiState.value.isOrderSaving)
        assertEquals(
            CategoryListEffect.Message(R.string.error_category_reorder_failed),
            viewModel.effects.first(),
        )
    }

    private fun categories() = listOf(
        category("a", 0),
        category("b", 1),
        category("c", 2),
    )

    private fun category(id: String, sortOrder: Int) = Category(
        id = id,
        name = id.uppercase(),
        colorIndex = 0,
        iconName = "Category",
        endHour = 0,
        sortOrder = sortOrder,
    )
}

private class FakeCategoryRepository(initialCategories: List<Category>) : CategoryRepository {
    private val categories = MutableStateFlow(initialCategories)
    var reorderResult: Result<Unit> = Result.success(Unit)
    var lastOrderedIds: List<String>? = null
    var reorderGate: CompletableDeferred<Unit>? = null

    override fun observeCategories() = categories

    override suspend fun getCategory(id: String): Category? = categories.value.firstOrNull { it.id == id }

    override suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
        endHour: Int,
    ): Result<String> = Result.failure(UnsupportedOperationException())

    override suspend fun reorderCategories(orderedIds: List<String>): Result<Unit> {
        lastOrderedIds = orderedIds
        reorderGate?.await()
        return reorderResult.onSuccess {
            val byId = categories.value.associateBy(Category::id)
            categories.value = orderedIds.mapIndexed { index, id ->
                requireNotNull(byId[id]).copy(sortOrder = index)
            }
        }
    }

    override suspend fun deleteCategory(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}
