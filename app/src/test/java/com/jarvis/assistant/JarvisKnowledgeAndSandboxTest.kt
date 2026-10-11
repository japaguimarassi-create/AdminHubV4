package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisKnowledgeAndSandboxTest {
    private val facts = listOf(
        JarvisKnowledgeFact("photosynthesis", "Biologia", "fotossíntese", "O que é fotossíntese?",
            "Plantas usam energia luminosa para transformar energia.", listOf("fotossíntese", "o que é fotossíntese", "explique fotossíntese")),
        JarvisKnowledgeFact("mul-17-19", "Matemática", "17 vezes 19", "Quanto é 17 vezes 19?",
            "17 × 19 = 323.", listOf("17 vezes 19", "17 x 19", "quanto é 17 vezes 19"))
    )

    @Test
    fun knowledgeFindsOfflineScienceFact() {
        val result = JarvisKnowledgeIndex.search("O que é fotossíntese?", facts)
        assertNotNull(result)
        assertEquals("photosynthesis", result?.fact?.id)
    }

    @Test
    fun knowledgeFindsExactArithmeticCard() {
        val result = JarvisKnowledgeIndex.search("quanto é 17 vezes 19", facts)
        assertEquals("mul-17-19", result?.fact?.id)
        assertTrue(result?.fact?.answer?.contains("323") == true)
    }

    @Test
    fun ambiguousQuestionDoesNotGuess() {
        assertNull(JarvisKnowledgeIndex.search("qual é a diferença entre", facts))
    }

    @Test
    fun internalSandboxAllowsOnlyDeclaredReadOnlyCommands() {
        assertEquals(SandboxOperation.KNOWLEDGE_SEARCH, JarvisInternalSandbox.parseCommand("knowledge.search força").operation)
        assertEquals(SandboxOperation.MATH_EVAL, JarvisInternalSandbox.parseCommand("math.eval (12 + 4) / 2").operation)
        assertEquals(SandboxOperation.APPS_LIST, JarvisInternalSandbox.parseCommand("apps list").operation)
    }

    @Test
    fun sandboxBlocksUnrestrictedShell() {
        assertEquals(SandboxOperation.BLOCKED, JarvisInternalSandbox.parseCommand("shell rm -rf /").operation)
        assertEquals(SandboxOperation.BLOCKED, JarvisInternalSandbox.parseCommand("bash").operation)
    }

    @Test
    fun sandboxPlansDoNotExecuteActions() {
        val command = JarvisInternalSandbox.parseCommand("plan.open YouTube")
        assertEquals(SandboxOperation.PLAN_OPEN, command.operation)
        assertFalse(command.argument.isBlank())
        assertTrue(JarvisInternalSandbox.parseCommand("plan.web gatos").operation == SandboxOperation.PLAN_WEB)
    }
}
