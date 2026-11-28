package org.bakasu.bakasu

import org.bakasu.bakasu.di.platformModules as managerPlatformModules

class KernelSUApplication : BaseKernelSUApplication() {
    override val platformModules = managerPlatformModules
}
