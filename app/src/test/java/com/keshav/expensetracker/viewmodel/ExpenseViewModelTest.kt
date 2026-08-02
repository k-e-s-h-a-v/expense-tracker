package com.keshav.expensetracker.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseViewModelTest {
    @Test
    fun `filter snapshot round trips through shared prefs formatting`() {
        val viewModel = ExpenseViewModel()

        val start = 1_700_000_000_000L
        val end = 1_700_100_000_000L
        viewModel.updateDateRange(start, end)
        viewModel.toggleSenderFilter("bank")
        viewModel.toggleMerchantFilter("store")
        viewModel.updateSearchQuery("coffee AND card")

        val snapshot = viewModel.buildFilterSnapshot()
        val restored = snapshot

        assertEquals(start, restored.startDateMs)
        assertEquals(end, restored.endDateMs)
        assertEquals(setOf("bank"), restored.selectedSenders)
        assertEquals(setOf("store"), restored.selectedMerchants)
        assertEquals("coffee AND card", restored.searchQuery)
    }

    @Test
    fun `search supports case insensitive AND and OR logic`() {
        assertEquals(true, ExpenseViewModel.matchesSearchQuery("coffee AND card", "Card payment at Coffee House"))
        assertEquals(true, ExpenseViewModel.matchesSearchQuery("fuel OR coffee AND card", "Coffee card payment"))
        assertEquals(true, ExpenseViewModel.matchesSearchQuery("fuel OR coffee", "Fuel purchase"))
        assertEquals(false, ExpenseViewModel.matchesSearchQuery("coffee AND taxi", "Coffee card payment"))
    }
}
