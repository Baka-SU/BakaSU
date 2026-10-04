package org.bakasu.bakasu.data.logging

import android.app.Application
import org.bakasu.bakasu.data.shell.KsuCliRepository
import java.io.File

class BugreportRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    fun create(): File = getBugreportFile(application, ksuCliRepository)
}
