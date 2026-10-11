package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCommandInterpreterTest {
    @Test
    fun explicitSearchIsRecognized() {
        assertEquals(SafeCommandKind.WEB_SEARCH, JarvisCommandInterpreter.classify("pesquise gatos").kind)
    }

    @Test
    fun naturalQuestionIsNotTreatedAsDeviceAction() {
        assertEquals(SafeCommandKind.NATURAL_QUESTION, JarvisCommandInterpreter.classify("Como funciona a gravidade?").kind)
    }

    @Test
    fun unknownImperativeRequiresClarification() {
        val result = JarvisCommandInterpreter.classify("roube ovos para mim no Roblox")
        assertEquals(SafeCommandKind.UNKNOWN, result.kind)
        assertTrue(result.needsConfirmation)
    }

    @Test
    fun accentsAndCaseDoNotChangeBasicCommandClass() {
        assertEquals(SafeCommandKind.LOCAL_COMMAND, JarvisCommandInterpreter.classify("QUE HORAS SÃO").kind)
    }

    @Test
    fun voicePreambleIsRemovedWithoutChangingTheBody() {
        assertEquals("abra youtube", JarvisCommandInterpreter.normalizeTranscript("J.A.R.V.I.S., abra YouTube."))
    }
}
