package com.jellio.tv.ui.home

import com.jellio.tv.data.model.BaseItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SlidingRowDeduplicatorTest {

    private fun createItem(id: String, name: String, year: Int? = 2020): BaseItemDto =
        BaseItemDto(Id = id, Name = name, ProductionYear = year)

    @Test
    fun filtersIntraRowDuplicatesByIdOrTitleAndYear() {
        val deduplicator = SlidingRowDeduplicator(windowSize = 2, viewportLimit = 6)

        val item1 = createItem("1", "Inception", 2010)
        val item1DuplicateId = createItem("1", "Different Title", 2020)
        val item1DuplicateTitle = createItem("2", "Inception", 2010)
        val itemDifferentYear = createItem("3", "Inception", 2024)
        val itemCaseInsensitive = createItem("4", "inception", 2024)
        val itemUnique = createItem("5", "Interstellar", 2014)

        val items = listOf(
            item1,
            item1DuplicateId,
            item1DuplicateTitle,
            itemDifferentYear,
            itemCaseInsensitive,
            itemUnique,
        )

        val result = deduplicator.dedupe(items, limit = 20)
        val resultIds = result.map { it.Id }

        assertEquals(listOf("1", "3", "5"), resultIds)
    }

    @Test
    fun excludesRow1ItemsFromFirstSixSlotsOfRow2() {
        val deduplicator = SlidingRowDeduplicator(windowSize = 2, viewportLimit = 6)

        val row1Items = (1..6).map { createItem("r1_$it", "Movie $it", 2020) }
        val r1Result = deduplicator.dedupe(row1Items, limit = 20)
        assertEquals(6, r1Result.size)

        // Row 2 candidate items start with the row 1 items, followed by fresh items
        val row2Fresh = (1..6).map { createItem("r2_$it", "Fresh $it", 2020) }
        val row2Candidates = row1Items + row2Fresh

        val r2Result = deduplicator.dedupe(row2Candidates, limit = 20)
        val viewportIds = r2Result.take(6).map { it.Id }

        // The first 6 slots must contain only fresh items, not row 1 items
        assertEquals(listOf("r2_1", "r2_2", "r2_3", "r2_4", "r2_5", "r2_6"), viewportIds)
        assertFalse(viewportIds.any { it.startsWith("r1_") })
    }

    @Test
    fun allowsRow1ItemsInSlot7AndBeyondOfRow2() {
        val deduplicator = SlidingRowDeduplicator(windowSize = 2, viewportLimit = 6)

        val sharedItem = createItem("shared", "Shared Classic", 1999)
        val row1Others = (1..5).map { createItem("r1_$it", "Row1 Item $it", 2020) }
        deduplicator.dedupe(listOf(sharedItem) + row1Others, limit = 20)

        // Row 2 has 6 fresh items followed by the shared item from row 1, then more fresh items
        val row2FreshFirst6 = (1..6).map { createItem("fresh_$it", "Fresh Movie $it", 2020) }
        val row2Subsequent = (7..10).map { createItem("fresh_$it", "Fresh Movie $it", 2020) }
        val row2Candidates = row2FreshFirst6 + listOf(sharedItem) + row2Subsequent

        val r2Result = deduplicator.dedupe(row2Candidates, limit = 20)

        // First 6 slots are fresh items
        assertEquals(row2FreshFirst6.map { it.Id }, r2Result.take(6).map { it.Id })

        // Slot 7 (index 6) contains the item from row 1
        assertTrue(r2Result.size >= 7)
        assertEquals("shared", r2Result[6].Id)
    }

    @Test
    fun supportsDistanceDecayWithWindowSizeOne() {
        val deduplicator = SlidingRowDeduplicator(windowSize = 1, viewportLimit = 6)

        val itemA = createItem("a", "Alpha", 2020)
        val row1Items = listOf(itemA) + (1..5).map { createItem("r1_$it", "Row1 $it", 2020) }
        deduplicator.dedupe(row1Items, limit = 20)

        // Row 2 has fresh items (evicting row 1 because windowSize is 1)
        val row2Items = (1..6).map { createItem("r2_$it", "Row2 $it", 2020) }
        deduplicator.dedupe(row2Items, limit = 20)

        // Row 3: item A should now be allowed in the first 6 slots (distance decay)
        val row3Candidates = listOf(itemA) + (1..5).map { createItem("r3_$it", "Row3 $it", 2020) }
        val r3Result = deduplicator.dedupe(row3Candidates, limit = 20)

        assertEquals("a", r3Result.first().Id)
    }

    @Test
    fun supportsDistanceDecayWithWindowSizeTwo() {
        val deduplicator = SlidingRowDeduplicator(windowSize = 2, viewportLimit = 6)

        val itemA = createItem("a", "Alpha", 2020)
        val row1Items = listOf(itemA) + (1..5).map { createItem("r1_$it", "Row1 $it", 2020) }
        deduplicator.dedupe(row1Items, limit = 20)

        // Row 2: fresh items (window now contains row 1 and row 2)
        val row2Items = (1..6).map { createItem("r2_$it", "Row2 $it", 2020) }
        deduplicator.dedupe(row2Items, limit = 20)

        // Row 3: item A is still in the window of 2, so it is excluded from first 6 slots
        val row3Fresh = (1..6).map { createItem("r3_$it", "Row3 $it", 2020) }
        val row3Candidates = listOf(itemA) + row3Fresh
        val r3Result = deduplicator.dedupe(row3Candidates, limit = 20)
        // itemA was excluded from first 6 slots of row 3
        assertFalse(r3Result.take(6).map { it.Id }.contains("a"))

        // After row 3 finishes, window contains row 2 and row 3 (row 1 is evicted)
        // Row 4: item A is now allowed in the first 6 slots (distance decay)
        val row4Candidates = listOf(itemA) + (1..5).map { createItem("r4_$it", "Row4 $it", 2020) }
        val r4Result = deduplicator.dedupe(row4Candidates, limit = 20)

        assertEquals("a", r4Result.first().Id)
    }

    @Test
    fun ensuresThreadSafetyUnderConcurrentCalls() {
        val deduplicator = SlidingRowDeduplicator(windowSize = 3, viewportLimit = 6)
        val threadCount = 16
        val iterationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val items = (0 until 12).map { idx ->
                            createItem("t${t}_i${i}_$idx", "Title_${t}_${i}_${idx}", 2000 + idx)
                        }
                        val deduped = deduplicator.dedupe(items, limit = 10)
                        assertTrue("Expected kept items to not be empty", deduped.isNotEmpty())
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue("Concurrent execution did not complete in time", completed)
        assertTrue("Unexpected errors during concurrent deduplication: $errors", errors.isEmpty())
    }
}
