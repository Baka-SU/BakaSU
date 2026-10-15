package org.bakasu.bakasu.data.registry

import org.bakasu.bakasu.data.module.registry.RegistryFormatException
import org.bakasu.bakasu.data.module.registry.RegistryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RegistryKindTest {
    private fun detail(kind: String = "", metamodule: Boolean = false): String = """
        {"schemaVersion":1,"buildId":"0123456789abcdef","id":"test_script",$kind
         "metamodule":$metamodule,"detail":"api/v1/modules/test_script.json","updatedAt":"",
         "stable":{"version":"1","versionCode":1,"publishedAt":""},
         "source":{"host":"github","fullName":"owner/repo","url":"https://github.com/owner/repo","commit":""}}
    """.trimIndent()

    @Test
    fun validatesDetailKindAndDefaultsMissingKindToModule() {
        val parser = RegistryParser()
        assertEquals("module", parser.parseDetail(detail()).kind)
        assertEquals("script", parser.parseDetail(detail("\"kind\":\"script\",")).kind)
        assertThrows(RegistryFormatException::class.java) { parser.parseDetail(detail("\"kind\":\"script\",", metamodule = true)) }
        assertThrows(RegistryFormatException::class.java) { parser.parseDetail(detail("\"kind\":\"unknown\",")) }
    }

    @Test
    fun keepsModulesAndScriptsButHidesUnknownAndMetamoduleScripts() {
        val entries = listOf(
            "module_default" to "",
            "module_explicit" to "\"kind\":\"module\",",
            "script_valid" to "\"kind\":\"script\",",
            "script_meta" to "\"kind\":\"script\",\"metamodule\":true,",
            "entry_unknown" to "\"kind\":\"unknown\",",
        ).joinToString(",") { (id, kind) ->
            """{"id":"$id",$kind"detail":"api/v1/modules/$id.json","stable":{"version":"1","versionCode":1,"publishedAt":""},"updatedAt":""}"""
        }
        val result = RegistryParser().parseModules("""{"schemaVersion":1,"buildId":"0123456789abcdef","modules":[$entries]}""")
        assertEquals(listOf("module_default", "module_explicit", "script_valid"), result.modules.map { it.id })
        assertEquals(listOf("module", "module", "script"), result.modules.map { it.kind })
    }
}
