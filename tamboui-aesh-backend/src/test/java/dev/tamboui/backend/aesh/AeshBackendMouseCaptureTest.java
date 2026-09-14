/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.backend.aesh;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AeshBackendMouseCaptureTest {

    private static final String ANY_EVENT_ON = "[?1003h";
    private static final String ANY_EVENT_OFF = "[?1003l";

    @Test
    @DisplayName("motion capture requests any-event tracking so hover (MOVE) events arrive")
    void motionCaptureEnablesAnyEventTracking() throws IOException {
        FakeConnection connection = new FakeConnection();
        AeshBackend backend = new AeshBackend(connection);

        backend.enableMouseCapture(true);

        assertThat(connection.output())
            .contains("[?1000h", "[?1002h", ANY_EVENT_ON, "[?1006h");
    }

    @Test
    @DisplayName("plain capture does not request any-event tracking")
    void plainCaptureDoesNotEnableAnyEventTracking() throws IOException {
        // Motion reports one event per cell crossed, so it must stay opt-in.
        FakeConnection connection = new FakeConnection();
        AeshBackend backend = new AeshBackend(connection);

        backend.enableMouseCapture(false);
        backend.enableMouseCapture();

        assertThat(connection.output())
            .contains("[?1000h", "[?1002h")
            .doesNotContain(ANY_EVENT_ON);
    }

    @Test
    @DisplayName("disabling capture clears any-event tracking")
    void disableClearsAnyEventTracking() throws IOException {
        // Without the reset the terminal keeps streaming motion to the shell after exit.
        FakeConnection connection = new FakeConnection();
        AeshBackend backend = new AeshBackend(connection);

        backend.enableMouseCapture(true);
        backend.disableMouseCapture();

        String output = connection.output();
        assertThat(output.indexOf(ANY_EVENT_OFF)).isGreaterThan(output.indexOf(ANY_EVENT_ON));
    }

    private static final class FakeConnection implements Connection {
        private final StringBuilder output = new StringBuilder();
        private final Consumer<int[]> stdout = codePoints -> {
            for (int codePoint : codePoints) {
                output.appendCodePoint(codePoint);
            }
        };
        private Consumer<int[]> stdinHandler;
        private Consumer<Signal> signalHandler;
        private Consumer<Size> sizeHandler;
        private Consumer<Void> closeHandler;
        private Attributes attributes = new Attributes();

        @Override
        public Device device() {
            return null;
        }

        @Override
        public Size size() {
            return new Size(80, 24);
        }

        @Override
        public Consumer<int[]> stdinHandler() {
            return stdinHandler;
        }

        @Override
        public void setStdinHandler(Consumer<int[]> handler) {
            this.stdinHandler = handler;
        }

        @Override
        public Consumer<int[]> stdoutHandler() {
            return stdout;
        }

        @Override
        public boolean put(Capability capability, Object... params) {
            return false;
        }

        @Override
        public Consumer<Signal> signalHandler() {
            return signalHandler;
        }

        @Override
        public void setSignalHandler(Consumer<Signal> handler) {
            this.signalHandler = handler;
        }

        @Override
        public Consumer<Size> sizeHandler() {
            return sizeHandler;
        }

        @Override
        public void setSizeHandler(Consumer<Size> handler) {
            this.sizeHandler = handler;
        }

        @Override
        public Consumer<Void> closeHandler() {
            return closeHandler;
        }

        @Override
        public void setCloseHandler(Consumer<Void> handler) {
            this.closeHandler = handler;
        }

        @Override
        public void openBlocking() {
        }

        @Override
        public void openNonBlocking() {
        }

        @Override
        public void close() {
        }

        @Override
        public Attributes attributes() {
            return attributes;
        }

        @Override
        public void setAttributes(Attributes attributes) {
            this.attributes = attributes;
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }

        @Override
        public Charset inputEncoding() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public Charset outputEncoding() {
            return StandardCharsets.UTF_8;
        }

        String output() {
            return output.toString();
        }
    }
}
