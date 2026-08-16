/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.terminal;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.tamboui.internal.record.AnsiTerminalCapture;
import dev.tamboui.layout.Size;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BackendFactory#applyRecording(Backend)}.
 * <p>
 * Applications that build their own backend and pass it to {@code TuiConfig.Builder#backend(Backend)} bypass
 * {@link BackendFactory#create()}, which used to be the only place recording was applied. Such applications got a clean
 * exit with no {@code .cast} file and no interaction playback, which is indistinguishable from a successful run. These
 * tests pin the behaviour that makes recording work on that path.
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
    }

    @Test
    void returnsTheBackendUnchangedWhenRecordingIsDisabled() {
        Backend backend = new TestBackend(80, 24);

        assertThat(BackendFactory.applyRecording(backend)).isSameAs(backend);
    }

    /**
     * The enabled cases share one test because {@link dev.tamboui.internal.record.RecordingConfig} caches its config
     * process-wide once loaded, so a second test that loaded different values would get the first test's config back
     * and fail depending on execution order.
     */
    @Test
    void wrapsTheBackendOnceAndSizesItFromTheRecordingConfig() throws Exception {
        System.setProperty("tamboui.record", tempDir.resolve("out.cast").toString());
        System.setProperty("tamboui.record.width", "120");
        System.setProperty("tamboui.record.height", "30");

        Backend wrapped = BackendFactory.applyRecording(new TestBackend(80, 24));

        // The recording backend reports the configured cast size rather than the delegate's size,
        // which proves the config reached the wrapper instead of merely something being returned.
        assertThat(wrapped).isNotInstanceOf(TestBackend.class);
        assertThat(wrapped.size()).isEqualTo(new Size(120, 30));

        // A caller may wrap its own backend and then hand it to TuiRunner, which wraps again. Stacking
        // would give the outer wrapper an interaction player of its own, so the tape would be consumed
        // by the wrong layer and the inner recorder would never see any input.
        assertThat(BackendFactory.applyRecording(wrapped)).isSameAs(wrapped);
    }
}
