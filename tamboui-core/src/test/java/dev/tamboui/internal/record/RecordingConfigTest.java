/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.internal.record;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RecordingConfig} property handling, in particular enabling
 * recording with only {@code tamboui.record.config} set: naming the input tape is
 * the explicit act, and the cast output is derived from the tape name so agents
 * and scripts can drive-and-record with a single property.
 */
class RecordingConfigTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        if (AnsiTerminalCapture.isInstalled()) {
            AnsiTerminalCapture.uninstall();
        }
        System.clearProperty("tamboui.record");
        System.clearProperty("tamboui.record.config");
        RecordingConfig.clearActive();
    }

    @Test
    @DisplayName("config property alone enables recording with derived .cast output")
    void configAloneDerivesCastOutput() {
        Path tape = tempDir.resolve("script.tape");
        System.setProperty("tamboui.record.config", tape.toString());

        assertThat(RecordingConfig.isEnabled()).isTrue();
        RecordingConfig config = RecordingConfig.load();

        assertThat(config).isNotNull();
        assertThat(config.configFile()).isEqualTo(tape);
        assertThat(config.outputPath()).isEqualTo(tempDir.resolve("script.cast"));
    }

    @Test
    @DisplayName("derived output appends .cast when the config file has no extension")
    void derivedOutputWithoutExtension() {
        Path tape = tempDir.resolve("script");
        System.setProperty("tamboui.record.config", tape.toString());

        RecordingConfig config = RecordingConfig.load();

        assertThat(config).isNotNull();
        assertThat(config.outputPath()).isEqualTo(tempDir.resolve("script.cast"));
    }

    @Test
    @DisplayName("explicit output property wins over derivation")
    void explicitOutputWins() {
        Path tape = tempDir.resolve("script.tape");
        Path cast = tempDir.resolve("elsewhere.cast");
        System.setProperty("tamboui.record", cast.toString());
        System.setProperty("tamboui.record.config", tape.toString());

        RecordingConfig config = RecordingConfig.load();

        assertThat(config).isNotNull();
        assertThat(config.outputPath()).isEqualTo(cast);
        assertThat(config.configFile()).isEqualTo(tape);
    }

    @Test
    @DisplayName("recording stays disabled when neither property is set")
    void disabledWithoutProperties() {
        assertThat(RecordingConfig.isEnabled()).isFalse();
        assertThat(RecordingConfig.load()).isNull();
    }
}
