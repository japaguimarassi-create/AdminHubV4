package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JarvisAppSequenceParserTest {
    private val apps = listOf("YouTube", "Roblox", "WhatsApp", "Google Maps")

    @Test
    fun separatesInstalledAppFromAdditionalInstructions() {
        val result = JarvisAppSequenceParser.parse("abra o Roblox e depois roube ovos", apps)
        assertEquals("Roblox", result?.appLabel)
        assertEquals("roube ovos", result?.followUp)
    }

    @Test
    fun handlesAppAliases() {
        val result = JarvisAppSequenceParser.parse("abrir yt e pesquise gatos", apps)
        assertEquals("YouTube", result?.appLabel)
        assertEquals("pesquise gatos", result?.followUp)
    }

    @Test
    fun separatesConditionalFollowUp() {
        val result = JarvisAppSequenceParser.parse("abra Roblox, se não tiver velocidade, pegue", apps)
        assertEquals("Roblox", result?.appLabel)
        assertEquals("nao tiver velocidade pegue", result?.followUp)
    }

    @Test
    fun ordinaryLaunchHasNoFollowUp() {
        assertNull(JarvisAppSequenceParser.parse("abrir YouTube", apps))
    }

    @Test
    fun unknownAppIsNotFalselyResolved() {
        assertNull(JarvisAppSequenceParser.parse("abra AppInexistente e depois pesquise gatos", apps))
    }
}
