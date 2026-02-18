/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2025, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.httpclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertigo.core.node.AutoCloseableNode;
import io.vertigo.core.node.component.di.DIInjector;
import io.vertigo.core.node.config.NodeConfig;
import io.vertigo.core.param.Param;

/**
 * Tests for {@link HttpClientConnector}.
 *
 * <p>No external HTTP server is required: the connector is a factory and does not
 * open any connection during construction or node startup.</p>
 */
public class HttpClientConnectorTest {

	@Inject
	private HttpClientConnector httpClientConnector;

	private AutoCloseableNode node;

	@BeforeEach
	public final void setUp() {
		node = new AutoCloseableNode(buildNodeConfig("main", "http://example.com"));
		DIInjector.injectMembers(this, node.getComponentSpace());
	}

	@AfterEach
	public final void tearDown() {
		if (node != null) {
			node.close();
		}
	}

	@Test
	public void testGetClientReturnsNonNull() {
		final HttpClient client = httpClientConnector.getClient();
		assertNotNull(client);
	}

	@Test
	public void testGetClientIsAFactory() {
		final HttpClient client1 = httpClientConnector.getClient();
		final HttpClient client2 = httpClientConnector.getClient();
		assertNotNull(client1);
		assertNotNull(client2);
		assertNotSame(client1, client2, "getClient() must return a new HttpClient instance on each call");
	}

	@Test
	public void testGetUrlPrefix() {
		assertEquals("http://example.com", httpClientConnector.getUrlPrefix());
	}

	@Test
	public void testGetName() {
		assertEquals("main", httpClientConnector.getName());
	}

	@Test
	public void testUrlPrefixMustNotEndWithSlash() {
		assertThrows(Exception.class, () -> new AutoCloseableNode(buildNodeConfig("main", "http://example.com/")));
	}

	@Test
	public void testUrlPrefixMustStartWithHttp() {
		assertThrows(Exception.class, () -> new AutoCloseableNode(buildNodeConfig("main", "ftp://example.com")));
	}

	private static NodeConfig buildNodeConfig(final String name, final String urlPrefix) {
		return NodeConfig.builder()
				.addModule(new HttpClientFeatures()
						.withHttpClient(
								Param.of("name", name),
								Param.of("urlPrefix", urlPrefix))
						.build())
				.build();
	}

}
