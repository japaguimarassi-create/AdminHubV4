package com.jarvis.assistant

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
    @Test
    fun parsesNaturalCommandWithoutComma() {
        val result = LocalAutomationEngine.parse("Quando abrir YouTube pesquise gatos")
        assertNotNull(result)
        assertEquals("YouTube", result!!.targetApp)
        assertEquals(listOf(LocalAction(LocalActionType.SEARCH, "gatos")), result.actions)
    }

    @Test
    fun parsesWaitDurationInSeconds() {
        val result = LocalAutomationEngine.parse("aguarde 2 segundos")
        assertNotNull(result)
        assertEquals(LocalAction(LocalActionType.WAIT, "2000"), result!!.actions.single())
    }

    @Test
    fun supportsLocalMacroTeachingSyntax() {
        assertEquals(
            LearnedCommand("abrir aula", "abra YouTube"),
            JarvisMemory.parseTeachingCommand("ensine abrir aula => abra YouTube")
        )
    }

    @Test
    fun neuralCoreHasExactly2678HiddenUnits() {
        assertEquals(2678, JarvisLocalBrain.HIDDEN_NEURONS)
    }

    @Test
    fun parsesOpenAppWithMultipleFollowUpActions() {
        val result = LocalAutomationEngine.parse(
            "abra Chrome e depois pesquise gatos e depois toque em primeiro resultado"
        )
        assertNotNull(result)
        assertEquals(
            listOf(
                LocalAction(LocalActionType.OPEN_APP, "Chrome"),
                LocalAction(LocalActionType.SEARCH, "gatos"),
                LocalAction(LocalActionType.CLICK_TEXT, "primeiro resultado")
            ),
            result!!.actions
        )
    }

    @Test
    fun recognizesAppActionAndWaitAsAPlan() {
        val result = LocalAutomationEngine.parse("abrir YouTube e depois aguarde 2 segundos")
        assertNotNull(result)
        assertEquals(LocalActionType.OPEN_APP, result!!.actions.first().type)
        assertEquals(LocalAction(LocalActionType.WAIT, "2000"), result.actions.last())
    }

}
