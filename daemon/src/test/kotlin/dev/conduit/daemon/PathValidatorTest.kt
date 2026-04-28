package dev.conduit.daemon

import dev.conduit.daemon.service.PathValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PathValidatorTest {

    @Test
    fun `validateRelativePath rejects dot-dot`() {
        assertFailsWith<ApiException> { PathValidator.validateRelativePath("../etc/passwd") }
        assertFailsWith<ApiException> { PathValidator.validateRelativePath("foo/../../bar") }
        assertFailsWith<ApiException> { PathValidator.validateRelativePath("a..b") }
    }

    @Test
    fun `validateRelativePath rejects absolute unix path`() {
        assertFailsWith<ApiException> { PathValidator.validateRelativePath("/etc/passwd") }
    }

    @Test
    fun `validateRelativePath rejects absolute windows path`() {
        assertFailsWith<ApiException> { PathValidator.validateRelativePath("\\Windows\\System32") }
    }

    @Test
    fun `validateRelativePath accepts valid relative path`() {
        PathValidator.validateRelativePath("config/server.properties")
        PathValidator.validateRelativePath("mods/foo.jar")
        PathValidator.validateRelativePath("")
    }

    @Test
    fun `sanitizeFileName strips directory components`() {
        assertEquals("evil.jar", PathValidator.sanitizeFileName("../../../evil.jar"))
        assertEquals("foo.jar", PathValidator.sanitizeFileName("dir/sub/foo.jar"))
    }

    @Test
    fun `sanitizeFileName rejects dot-dot only`() {
        assertFailsWith<ApiException> { PathValidator.sanitizeFileName("..") }
    }

    @Test
    fun `sanitizeFileName rejects blank`() {
        assertFailsWith<ApiException> { PathValidator.sanitizeFileName("") }
    }

    @Test
    fun `sanitizeFileName accepts plain filename`() {
        assertEquals("mod.jar", PathValidator.sanitizeFileName("mod.jar"))
    }
}
