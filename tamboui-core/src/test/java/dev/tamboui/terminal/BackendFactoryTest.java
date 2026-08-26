/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.terminal;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BackendFactory}'s provider resolution logic.
 * <p>
 * Uses reflection to test the private {@code resolveProviders} method directly,
 * verifying that a comma-separated provider specification acts as a preference
 * order with fallback rather than a mandatory list.
 */
class BackendFactoryTest {

    @Test
    @DisplayName("resolveProviders skips unavailable provider and falls back to next")
    void resolveProviders_skipsUnavailableProvider() throws Exception {
        Method method = BackendFactory.class.getDeclaredMethod("resolveProviders", String.class, List.class);
        method.setAccessible(true);

        // Only "beta" is available; "alpha" is not (simulating a provider that
        // failed to load, e.g. because it targets a newer Java version)
        List<BackendProvider> available = new ArrayList<>();
        available.add(new StubBackendProvider("beta"));

        @SuppressWarnings("unchecked")
        List<BackendProvider> resolved = (List<BackendProvider>) method.invoke(null, "alpha,beta", available);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).name()).isEqualTo("beta");
    }

    @Test
    @DisplayName("resolveProviders preserves order when all providers are available")
    void resolveProviders_preservesOrder() throws Exception {
        Method method = BackendFactory.class.getDeclaredMethod("resolveProviders", String.class, List.class);
        method.setAccessible(true);

        List<BackendProvider> available = new ArrayList<>();
        available.add(new StubBackendProvider("beta"));
        available.add(new StubBackendProvider("alpha"));

        @SuppressWarnings("unchecked")
        List<BackendProvider> resolved = (List<BackendProvider>) method.invoke(null, "alpha,beta", available);

        assertThat(resolved).hasSize(2);
        assertThat(resolved.get(0).name()).isEqualTo("alpha");
        assertThat(resolved.get(1).name()).isEqualTo("beta");
    }

    @Test
    @DisplayName("resolveProviders throws when no specified provider is available")
    void resolveProviders_throwsWhenNoneAvailable() throws Exception {
        Method method = BackendFactory.class.getDeclaredMethod("resolveProviders", String.class, List.class);
        method.setAccessible(true);

        List<BackendProvider> available = new ArrayList<>();
        available.add(new StubBackendProvider("gamma"));

        assertThatThrownBy(() -> method.invoke(null, "alpha,beta", available))
                .hasCauseInstanceOf(BackendException.class)
                .cause()
                .hasMessageContaining("alpha,beta");
    }

    /**
     * Minimal BackendProvider stub for testing provider resolution.
     */
    private static class StubBackendProvider implements BackendProvider {
        private final String providerName;

        StubBackendProvider(String name) {
            this.providerName = name;
        }

        @Override
        public String name() {
            return providerName;
        }

        @Override
        public Backend create() throws IOException {
            throw new IOException("stub — not a real backend");
        }
    }
}
