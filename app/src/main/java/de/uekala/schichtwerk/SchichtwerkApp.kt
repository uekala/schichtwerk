package de.uekala.schichtwerk

import android.app.Application
import de.uekala.schichtwerk.bg.Notifier
import de.uekala.schichtwerk.bg.PrintCheckWorker

class SchichtwerkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        PrintCheckWorker.schedule(this)
    }
}
