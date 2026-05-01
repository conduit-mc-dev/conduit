package dev.conduit.daemon

import org.junit.Ignore
import kotlin.test.Test

/**
 * Tests for parsing mod metadata from JAR files.
 *
 * Mod loaders embed metadata in different files inside the JAR:
 * - Fabric/Quilt: fabric.mod.json (JSON with id, name, version, environment, depends)
 * - NeoForge/Forge: META-INF/mods.toml (TOML with modId, displayName, version, side)
 * - Paper/Spigot/Bukkit: plugin.yml (YAML with name, version, api-version)
 *
 * TODO: Implement ModService.parseModMetadata(jarPath: Path): ParsedModMetadata
 * that extracts and normalizes metadata from these three formats.
 * Then replace this @Ignore stub test with real tests that:
 *  1. Create an in-memory JAR containing fabric.mod.json, parse, assert fields
 *  2. Create an in-memory JAR containing META-INF/mods.toml, parse, assert fields
 *  3. Create an in-memory JAR containing plugin.yml, parse, assert fields
 */
class ModMetadataParseTest {

    @Ignore("parseModMetadata() is not yet implemented in ModService")
    @Test
    fun `parse fabric mod json metadata from JAR`() {
        // Create in-memory JAR with fabric.mod.json using ZipOutputStream
        // Assert parsed fields: id, name, version, environment, dependencies

        // Example fabric.mod.json:
        // {
        //   "schemaVersion": 1,
        //   "id": "example-mod",
        //   "name": "Example Mod",
        //   "version": "1.0.0",
        //   "environment": "*",
        //   "depends": { "fabricloader": ">=0.14", "minecraft": ">=1.20" }
        // }
        TODO("Not yet implemented")
    }
}
