package com.jarvis.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService

class JarvisVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        showSession(Bundle(), 0)
    }
}
