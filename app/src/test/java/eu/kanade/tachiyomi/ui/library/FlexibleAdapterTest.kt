package eu.kanade.tachiyomi.ui.library

import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestMangaItem(val title: String) : AbstractFlexibleItem<RecyclerView.ViewHolder>(), IFilterable<String> {
    override fun getLayoutRes(): Int = 0
    override fun createViewHolder(view: android.view.View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): RecyclerView.ViewHolder = throw UnsupportedOperationException()
    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>?) {}
    override fun filter(constraint: String): Boolean = title.contains(constraint, ignoreCase = true)
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = title.hashCode()
}

class TestLibraryCategoryAdapter(items: List<IFlexible<*>>) : FlexibleAdapter<IFlexible<*>>(items, null, true) {
    init {
        setDisplayHeadersAtStartUp(true)
    }

    override fun setHasStableIds(hasStableIds: Boolean) {
        try {
            val obsField = RecyclerView.Adapter::class.java.getDeclaredField("mObservable")
            obsField.isAccessible = true
            val observable = obsField.get(this)
            val observersField = android.database.Observable::class.java.getDeclaredField("mObservers")
            observersField.isAccessible = true
            if (observersField.get(observable) == null) {
                observersField.set(observable, ArrayList<Any>())
            }
        } catch (e: Throwable) {
            // ignore
        }
        super.setHasStableIds(hasStableIds)
    }

    suspend fun performFilterAsync(mangas: List<TestMangaItem>, filterDelayMs: Long = 0) {
        val s = getFilter(String::class.java)
        setFilter(null)
        try {
            if (s.isNullOrBlank()) {
                updateDataSet(mangas)
            } else {
                val filteredManga = withContext(Dispatchers.Default) {
                    if (filterDelayMs > 0) delay(filterDelayMs)
                    mangas.filter { it.filter(s) }
                }
                currentCoroutineContext().ensureActive()
                updateDataSet(filteredManga)
                if (getFilter(String::class.java) == null) {
                    setFilter(s)
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }
}

class FlexibleAdapterTest {

    @Test
    fun testSearchGlobalItemAppearsWhenNoMangaMatches() = runBlocking {
        val manga1 = TestMangaItem("One Piece")
        val manga2 = TestMangaItem("Naruto")
        val adapter = TestLibraryCategoryAdapter(listOf(manga1, manga2))
        val searchItem = SearchGlobalItem()
        searchItem.string = "Bleach"

        // 1. User enters search query "Bleach"
        adapter.addScrollableHeader(searchItem)
        adapter.setFilter("Bleach")

        // 2. Perform filtering
        adapter.performFilterAsync(listOf(manga1, manga2))

        // 3. Verify that adapter itemCount is 1 and item at 0 is SearchGlobalItem
        assertEquals("Adapter should have 1 item (SearchGlobalItem) when no manga matches", 1, adapter.itemCount)
        assertTrue("Item at position 0 must be SearchGlobalItem", adapter.getItem(0) is SearchGlobalItem)
        assertEquals("SearchGlobalItem query string should match", "Bleach", (adapter.getItem(0) as SearchGlobalItem).string)

        // 4. User types "Bleach 2"
        searchItem.string = "Bleach 2"
        adapter.updateItem(searchItem)
        adapter.setFilter("Bleach 2")
        adapter.performFilterAsync(listOf(manga1, manga2))

        assertEquals("Adapter should still have 1 item", 1, adapter.itemCount)
        assertEquals("SearchGlobalItem query string should update", "Bleach 2", (adapter.getItem(0) as SearchGlobalItem).string)
    }

    @Test
    fun testConcurrentFilterCancellationPreventsStaleQuery() = runBlocking {
        val manga1 = TestMangaItem("One Piece")
        val manga2 = TestMangaItem("Naruto")
        val adapter = TestLibraryCategoryAdapter(listOf(manga1, manga2))
        val searchItem = SearchGlobalItem()

        // Fast typing simulation:
        // Job 1: Query = "O" (matches "One Piece", takes 100ms)
        adapter.addScrollableHeader(searchItem)
        searchItem.string = "O"
        adapter.setFilter("O")
        val job1: Job = launch {
            adapter.performFilterAsync(listOf(manga1, manga2), filterDelayMs = 100)
        }

        // Job 2: Query = "Bleach" arrives 10ms later (matches nothing, cancels Job 1)
        delay(10)
        job1.cancel()

        searchItem.string = "Bleach"
        adapter.setFilter("Bleach")
        val job2 = launch {
            adapter.performFilterAsync(listOf(manga1, manga2), filterDelayMs = 10)
        }

        job2.join()

        // After Job 2 completes and Job 1 was cancelled:
        assertTrue("Current filter should be 'bleach', not stale 'o'", adapter.getFilter(String::class.java).equals("Bleach", ignoreCase = true))
        assertEquals("Item count should be 1 (SearchGlobalItem only)", 1, adapter.itemCount)
        assertTrue("Item at 0 should be SearchGlobalItem", adapter.getItem(0) is SearchGlobalItem)
    }
}
