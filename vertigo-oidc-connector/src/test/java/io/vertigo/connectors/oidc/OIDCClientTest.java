/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2026, Vertigo.io, team@vertigo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertigo.connectors.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.node.AutoCloseableNode;
import io.vertigo.core.node.config.BootConfig;
import io.vertigo.core.node.config.NodeConfig;
import io.vertigo.core.plugins.resource.classpath.ClassPathResourceResolverPlugin;

/**
 * OIDC client without OpenID provider : metadata from a local file, or a provider that can't be reached.
 */
class OIDCClientTest {
	private static final String ISSUER = "https://sso.test/realms/test";
	private static final String UNREACHABLE_ISSUER = "http://127.0.0.1:1/realms/test";

	private static AutoCloseableNode node;

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void setUp() {
		//the OIDC client gets its components from the current node
		node = new AutoCloseableNode(NodeConfig.builder()
				.withBoot(BootConfig.builder()
						.addPlugin(ClassPathResourceResolverPlugin.class)
						.build())
				.build());
	}

	@AfterAll
	static void tearDown() {
		if (node != null) {
			node.close();
		}
	}

	@Test
	void testLogoutUrl() throws Exception {
		final var client = new OIDCClient(parameters(ISSUER, Optional.of(metadataFile()), false));
		assertEquals("https://sso.test/realms/test/logout?client_id=myApp"
				+ "&post_logout_redirect_uri=https%3A%2F%2Fapp.test%2Flogged-out"
				+ "&id_token_hint=header.payload.signature"
				+ "&ui_locales=en",
				client.getLogoutUrl(Optional.of(URI.create("https://app.test/logged-out")), Optional.of("header.payload.signature"), Optional.of(Locale.ENGLISH)));
	}

	@Test
	void testLogoutUrlMetadataUnavailable() {
		//dontFailAtStartup : the provider can't be reached at startup, the login is temporarily disabled
		final var client = new OIDCClient(parameters(UNREACHABLE_ISSUER, Optional.empty(), true));
		assertThrows(VSystemException.class, () -> client.getLogoutUrl(Optional.empty(), Optional.empty(), Optional.empty()));
	}

	private static OIDCParameters parameters(final String oidcUrl, final Optional<URL> metadataFile, final boolean dontFailAtStartup) {
		return new OIDCParameters(
				"myApp", Optional.empty(), oidcUrl, Optional.empty(),
				1000, 1000,
				metadataFile, "RS256", Boolean.FALSE, Boolean.TRUE,
				Optional.of("post_logout_redirect_uri"), Optional.of("id_token_hint"), Optional.of("ui_locales"),
				dontFailAtStartup,
				Optional.empty(), Optional.empty(),
				Optional.empty(), 3128);
	}

	private static URL metadataFile() throws Exception {
		final var metadata = """
				{
					"issuer": "%1$s",
					"authorization_endpoint": "%1$s/auth",
					"token_endpoint": "%1$s/token",
					"jwks_uri": "%1$s/certs",
					"end_session_endpoint": "%1$s/logout",
					"response_types_supported": ["code"],
					"subject_types_supported": ["public"],
					"id_token_signing_alg_values_supported": ["RS256"]
				}
				""".formatted(ISSUER);
		final var file = tempDir.resolve("openid-configuration.json");
		Files.writeString(file, metadata, StandardCharsets.UTF_8);
		return file.toUri().toURL();
	}
}
