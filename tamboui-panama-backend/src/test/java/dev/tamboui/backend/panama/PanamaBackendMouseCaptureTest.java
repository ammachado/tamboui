/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.backend.panama;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.tamboui.layout.Size;

import static org.assertj.core.api.Assertions.assertThat;

class PanamaBackendMouseCaptureTest {

    private static final String ANY_EVENT_ON = "[?1003h";
    private static final String ANY_EVENT_OFF = "[?1003l";

    @Test
    @DisplayName("motion capture requests any-event tracking so hover (MOVE) events arrive")
    void motionCaptureEnablesAnyEventTracking() throws IOException {
        // Panama is the backend picked on Java 22+, so dropping the flag here
        // silently disables hover for every app on a modern JDK.
        FakeTerminal terminal = new FakeTerminal();
        PanamaBackend backend = new PanamaBackend(terminal);

        backend.enableMouseCapture(true);

        assertThat(terminal.output())
            .contains("[?1000h", "[?1002h", ANY_EVENT_ON, "[?1006h");
    }

    @Test
    @DisplayName("plain capture does not request any-event tracking")
    void plainCaptureDoesNotEnableAnyEventTracking() throws IOException {
        // Motion reports one event per cell crossed, so it must stay opt-in.
        FakeTerminal terminal = new FakeTerminal();
        PanamaBackend backend = new PanamaBackend(terminal);

        backend.enableMouseCapture(false);
        backend.enableMouseCapture();

        assertThat(terminal.output())
            .contains("[?1000h", "[?1002h")
            .doesNotContain(ANY_EVENT_ON);
    }

    @Test
    @DisplayName("disabling capture clears any-event tracking")
    void disableClearsAnyEventTracking() throws IOException {
        // Without the reset the terminal keeps streaming motion to the shell after exit.
        FakeTerminal terminal = new FakeTerminal();
        PanamaBackend backend = new PanamaBackend(terminal);

        backend.enableMouseCapture(true);
        backend.disableMouseCapture();

        String output = terminal.output();
        assertThat(output.indexOf(ANY_EVENT_OFF)).isGreaterThan(output.indexOf(ANY_EVENT_ON));
    }

    private static final class FakeTerminal implements PlatformTerminal {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public void enableRawMode() {
        }

        @Override
        public void disableRawMode() {
        }

        @Override
        public Size getSize() {
            return new Size(80, 24);
        }

        @Override
        public int read(int timeoutMs) {
            return -1;
        }

        @Override
        public int peek(int timeoutMs) {
            return -1;
        }

        @Override
        public void write(byte[] data) throws IOException {
            output.write(data);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            output.write(buffer, offset, length);
        }

        @Override
        public void write(String s) throws IOException {
            output.write(s.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Charset getCharset() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public boolean isRawModeEnabled() {
            return false;
        }

        @Override
        public void onResize(Runnable handler) {
        }

        @Override
        public void close() {
        }

        String output() {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
