package com.mochisofts.mata.ui.category

import androidx.lifecycle.SavedStateHandle
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.repository.CategoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun cm029_emptyLoadingFetchFailuresAndOperationFailureStayDistinct() = runTest {
        val repository = FakeCategoryRepository(emptyList()).apply { failObserve = true }
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hasLoadError)

        repository.failObserve = false
        viewModel.retryLoad()
        runCurrent()
        assertFalse(viewModel.uiState.value.hasLoadError)
        assertTrue(viewModel.uiState.value.categories.isEmpty())

        repository.failGet = true
        viewModel.openEditor("missing")
        runCurrent()
        assertEquals(R.string.category_list_load_error, viewModel.uiState.value.editor?.errorMessageRes)

        repository.failGet = false
        repository.saveResult = Result.failure(IllegalStateException("save failed"))
        viewModel.openNewEditor()
        viewModel.setEditorName("draft")
        viewModel.saveEditor()
        runCurrent()
        assertEquals("draft", viewModel.uiState.value.editor?.name)
        assertEquals(R.string.error_category_save_failed, viewModel.uiState.value.editor?.errorMessageRes)
        assertTrue(requireNotNull(viewModel.uiState.value.editor).isDirty)
    }

    @Test
    fun dragOrder_isSavedAndReportsNewPosition() = runTest {
        val repository = FakeCategoryRepository(categories())
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
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
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
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
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
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

    @Test
    fun editorSave_keepsSavedCategorySelected() = runTest {
        val repository = FakeCategoryRepository(categories())
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
        runCurrent()

        viewModel.openEditor("a")
        runCurrent()
        viewModel.setEditorName("Updated")
        viewModel.saveEditor()
        runCurrent()

        assertEquals("a", viewModel.uiState.value.editor?.categoryId)
        assertEquals("Updated", viewModel.uiState.value.editor?.name)
        assertFalse(requireNotNull(viewModel.uiState.value.editor).isDirty)
        assertEquals(CategoryListEffect.CategorySaved(isNew = false), viewModel.effects.first())
    }

    @Test
    fun editorDelete_clearsSelection() = runTest {
        val repository = FakeCategoryRepository(categories())
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
        runCurrent()

        viewModel.openEditor("a")
        runCurrent()
        viewModel.deleteEditor()
        runCurrent()

        assertEquals(null, viewModel.uiState.value.editor)
        assertEquals(CategoryListEffect.CategoryDeleted, viewModel.effects.first())
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
        sortOrder = sortOrder,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun failedSaveKeepsDraftThenRetrySucceedsWithoutDuplicateWrite() = runTest {
        val repository = FakeCategoryRepository(emptyList()).apply {
            saveResult = Result.failure(IllegalStateException("write failed"))
            saveGate = CompletableDeferred()
        }
        val viewModel = CategoryEditorViewModel(SavedStateHandle(), repository)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("", viewModel.uiState.value.name)
        viewModel.setName("日常")

        viewModel.save()
        runCurrent()
        viewModel.save()
        assertEquals(1, repository.saveCount)
        assertTrue(viewModel.uiState.value.isSaving)

        repository.saveGate?.complete(Unit)
        runCurrent()
        assertEquals("日常", viewModel.uiState.value.name)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.errorMessageRes)

        repository.saveResult = Result.success("new")
        viewModel.save()
        runCurrent()

        assertEquals(2, repository.saveCount)
        assertEquals(CategoryEditorEffect.Saved("new", isNew = true), viewModel.effects.first())
    }

}

private class FakeCategoryRepository(initialCategories: List<Category>) : CategoryRepository {
    private val categories = MutableStateFlow(initialCategories)
    var reorderResult: Result<Unit> = Result.success(Unit)
    var saveResult: Result<String> = Result.success("new")
    var lastOrderedIds: List<String>? = null
    var reorderGate: CompletableDeferred<Unit>? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var saveCount = 0
    var failObserve = false
    var failGet = false

    override fun observeCategories(): Flow<List<Category>> = flow {
        if (failObserve) error("list load failed")
        emitAll(categories)
    }

    override suspend fun getCategory(id: String): Category? {
        if (failGet) error("form load failed")
        return categories.value.firstOrNull { it.id == id }
    }

    override suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
    ): Result<String> {
        saveCount += 1
        saveGate?.await()
        val savedId = id ?: "new"
        return saveResult.map { resultId ->
            val existing = categories.value.firstOrNull { it.id == savedId }
            val saved = Category(
                id = savedId,
                name = name,
                colorIndex = colorIndex,
                iconName = iconName,
                sortOrder = existing?.sortOrder ?: categories.value.size,
            )
            categories.value = categories.value.filterNot { it.id == savedId } + saved
            resultId.takeIf { id == null } ?: savedId
        }
    }

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

    override suspend fun deleteCategory(id: String): Result<Unit> {
        categories.value = categories.value.filterNot { it.id == id }
        return Result.success(Unit)
    }
}
