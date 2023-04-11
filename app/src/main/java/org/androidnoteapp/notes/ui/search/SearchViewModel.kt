package org.androidnoteapp.notes.ui.search

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.androidnoteapp.notes.data.model.Note
import org.androidnoteapp.notes.data.model.Notebook
import org.androidnoteapp.notes.data.repo.NoteRepository
import org.androidnoteapp.notes.data.repo.NotebookRepository
import org.androidnoteapp.notes.data.sync.core.SyncManager
import org.androidnoteapp.notes.preferences.PreferenceRepository
import org.androidnoteapp.notes.preferences.SortMethod
import org.androidnoteapp.notes.ui.common.AbstractNotesViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    noteRepository: NoteRepository,
    notebookRepository: NotebookRepository,
    preferenceRepository: PreferenceRepository,
    syncManager: SyncManager,
) : AbstractNotesViewModel(preferenceRepository, syncManager) {
    private val searchKeyData: MutableStateFlow<String> = MutableStateFlow("")

    var isFirstLoad = true

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    override val provideNotes = { sortMethod: SortMethod ->
        notebookRepository.getAll().distinctUntilChanged().flatMapLatest { notebooks ->
            searchKeyData.debounce(300).flatMapLatest { searchKey ->
                noteRepository
                    .getAll(sortMethod)
                    .map { notes ->
                        getSearchResults(
                            searchKey.trim(),
                            notes,
                            notebooks
                        )
                    }
            }
        }
    }

    private fun getSearchResults(
        searchKey: String,
        notes: List<Note>,
        notebooks: List<Notebook>,
    ): List<Note> = notes.filter { note ->
        fun String.matches(): Boolean = contains(searchKey, true)

        when (note.isList) {
            true -> note.taskList.any { it.content.matches() }
            false -> note.content.matches()
        } ||
            note.title.matches() ||
            note.attachments.any { it.description.matches() } ||
            note.tags.any { it.name.matches() } ||
            notebooks.any { it.name.matches() && it.id == note.notebookId }
    }

    fun setSearchQuery(query: String) = viewModelScope.launch {
        searchKeyData.emit(query)
    }
}
