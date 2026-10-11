package com.neuralhud.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAutomationEngineTest {
    @Test
    fun parsesAppTargetAndSearch() {
        val result = LocalAutomationEngine.parse("Quando abrir YouTube, pesquise gatos")
        assertNotNull(result)
        assertEquals("YouTube", result!!.targetApp)
        assertEquals(listOf(LocalAction(LocalActionType.SEARCH, "gatos")), result.actions)
    }

    @Test
    fun parsesTextAndEnterSequence() {
        val result = LocalAutomationEngine.parse("Quando abrir Chrome, digite https://example.com e depois pressione Enter")
        assertNotNull(result)
        assertEquals("Chrome", result!!.targetApp)
        assertEquals(
            listOf(
                LocalAction(LocalActionType.TYPE_TEXT, "https://example.com"),
                LocalAction(LocalActionType.ENTER)
            ),
            result.actions
        )
    }

    @Test
    fun parsesScrollingCommandWithAccents() {
        val result = LocalAutomationEngine.parse("Role para baixo")
        assertNotNull(result)
        assertEquals(LocalAction(LocalActionType.SCROLL_DOWN), result!!.actions.single())
    }

    @Test
    fun preservesSearchTextWithoutAnAppTarget() {
        val result = LocalAutomationEngine.parse("Pesquise clima no Rio de Janeiro")
        assertNotNull(result)
        assertNull(result!!.targetApp)
        assertEquals(LocalAction(LocalActionType.SEARCH, "clima no Rio de Janeiro"), result.actions.single())
    }

    @Test
    fun rejectsUnknownInstructionInsteadOfGuessing() {
        assertNull(LocalAutomationEngine.parse("Faça algo inteligente"))
    }
}
