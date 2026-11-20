package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import org.bakasu.bakasu.R

/** Keep app profiles, local templates and online template details visually consistent. */
internal fun wearProfileFieldIcon(field: Int) = when (field) {
    R.string.app_profile_template_id, R.string.wear_uid, R.string.wear_gid -> Icons.Rounded.Numbers
    R.string.app_profile_template_name -> Icons.Rounded.Edit
    R.string.app_profile_template_description -> Icons.Rounded.Description
    R.string.module_author -> Icons.Rounded.Person
    R.string.profile_namespace -> Icons.Rounded.Storage
    R.string.profile_groups -> Icons.Rounded.Group
    R.string.profile_flags -> Icons.Rounded.Flag
    else -> Icons.Rounded.Security
}
