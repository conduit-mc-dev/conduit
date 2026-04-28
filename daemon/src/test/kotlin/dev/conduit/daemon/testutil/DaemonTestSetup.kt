package dev.conduit.daemon.testutil

import dev.conduit.core.download.ModrinthClient
import dev.conduit.core.download.MojangClient
import dev.conduit.daemon.createMockMojangClient
import dev.conduit.daemon.module
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.core.model.InstanceState
import io.ktor.client.*
import io.ktor.server.testing.*
import java.nio.file.Files
import java.nio.file.Path

data class TestEnv(
    val tempDir: Path,
    val dataDir: DataDirectory,
    val store: InstanceStore,
)

fun ApplicationTestBuilder.setupTestModule(
    mojangClient: MojangClient? = createMockMojangClient(),
    modrinthClient: ModrinthClient? = null,
    loaderHttpClient: HttpClient? = null,
    instanceStore: InstanceStore? = null,
): TestEnv {
    val tempDir = Files.createTempDirectory("conduit-test")
    tempDir.toFile().deleteOnExit()
    val dataDir = DataDirectory(tempDir)
    val store = instanceStore ?: InstanceStore(dataDir)
    application {
        module(
            dataDirectory = dataDir,
            instanceStore = store,
            mojangClient = mojangClient,
            modrinthClient = modrinthClient,
            loaderHttpClient = loaderHttpClient,
        )
    }
    return TestEnv(tempDir, dataDir, store)
}

// Mock MojangClient 几乎瞬时完成下载，会把状态从 INITIALIZING 推进到 STOPPED；
// 部分测试需要观察 INITIALIZING 行为，因此先等后台任务收敛，再强制把状态钉回 INITIALIZING。
fun TestEnv.forceInitializing(instanceId: String) {
    val deadline = System.currentTimeMillis() + 5_000
    while (store.get(instanceId).state == InstanceState.INITIALIZING) {
        if (System.currentTimeMillis() > deadline) break
        Thread.sleep(10)
    }
    store.forceState(instanceId, InstanceState.INITIALIZING)
}
