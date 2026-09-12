/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.internal.record;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.tamboui.layout.Size;
import dev.tamboui.terminal.Backend;
import dev.tamboui.terminal.BackendFactory;
import dev.tamboui.terminal.TestBackend;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BackendFactory#recordIfEnabled(Backend)}.
 * <p>
 * Applications that build their own backend and pass it to {@code TuiConfig.Builder#backend(Backend)} bypass
 * {@code BackendFactory.create()}, which used to be the only place recording was applied. Such applications got a clean
 * exit with no {@code .cast} file and no interaction playback, which is indistinguishable from a successful run. These
 * tests pin the behaviour that makes recording work on that path.
 * <p>
 * The class lives in {@code dev.tamboui.internal.record} rather than next to {@link BackendFactory} so that
 * {@link RecordingConfig#clearActive()}, which is package-private, is reachable. {@link RecordingConfig} caches the
 * loaded config process-wide, so without that reset a temp-dir-backed config would leak into every later test in the
 * same JVM.
 */
class BackendFactoryRecordingTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        // load() installs a System.out capture; restore the real stream for the remaining tests
        if (AnsiTerminalCapture.isInstalled()) {
            AnsiTerminalCapture.uninstall();
        }
        System.clearProperty("tamboui.record");
        System.clearProperty("tamboui.record.width");
        System.clearProperty("tamboui.record.height");
        // The config is cached process-wide; drop it so the temp dir does not outlive this test
        RecordingConfig.clearActive();
    }

    @Test
    void returnsTheBackendUnchangedWhenRecordingIsDisabled() {
        Backend backend = new TestBackend(80, 24);

        assertThat(BackendFactory.recordIfEnabled(backend)).isSameAs(backend);
    }

    @Test
    void wrapsTheBackendOnceAndSizesItFromTheRecordingConfig() throws Exception {
        System.setProperty("tamboui.record", tempDir.resolve("out.cast").toString());
        System.setProperty("tamboui.record.width", "120");
        System.setProperty("tamboui.record.height", "30");

        Backend wrapped = BackendFactory.recordIfEnabled(new TestBackend(80, 24));

        // The recording backend reports the configured cast size rather than the delegate's size,
        // which proves the config reached the wrapper instead of merely something being returned.
        assertThat(wrapped).isNotInstanceOf(TestBackend.class);
        assertThat(wrapped.size()).isEqualTo(new Size(120, 30));

        // A caller may wrap its own backend and then hand it to TuiRunner, which wraps again. Stacking
        // would give the outer wrapper an interaction player of its own, so the tape would be consumed
        // by the wrong layer and the inner recorder would never see any input.
        assertThat(BackendFactory.recordIfEnabled(wrapped)).isSameAs(wrapped);
    }
}
