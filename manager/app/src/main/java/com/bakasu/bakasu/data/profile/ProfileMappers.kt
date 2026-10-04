package com.bakasu.bakasu.data.profile

import com.bakasu.bakasu.Natives
import com.bakasu.bakasu.domain.model.AppProfile

internal fun Natives.Profile.toDomain(): AppProfile = AppProfile(
    name = name,
    currentUid = currentUid,
    allowSu = allowSu,
    rootUseDefault = rootUseDefault,
    rootTemplate = rootTemplate,
    uid = uid,
    gid = gid,
    groups = groups,
    capabilities = capabilities,
    context = context,
    namespace = namespace,
    nonRootUseDefault = nonRootUseDefault,
    umountModules = umountModules,
    rules = rules,
    flags = flags,
)

internal fun AppProfile.toNative(): Natives.Profile = Natives.Profile(
    name = name,
    currentUid = currentUid,
    allowSu = allowSu,
    rootUseDefault = rootUseDefault,
    rootTemplate = rootTemplate,
    uid = uid,
    gid = gid,
    groups = groups,
    capabilities = capabilities,
    context = context,
    namespace = namespace,
    nonRootUseDefault = nonRootUseDefault,
    umountModules = umountModules,
    rules = rules,
    flags = flags,
)

