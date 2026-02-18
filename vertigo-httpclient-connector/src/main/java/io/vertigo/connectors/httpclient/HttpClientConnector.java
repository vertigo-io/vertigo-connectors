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

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;
import javax.net.ssl.SSLContext;

import io.vertigo.connectors.ssl.ConnectorSslUtil;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

/**
 * Connector that acts as a factory for {@link java.net.http.HttpClient} instances.
 *
 * <p>Unlike connectors that wrap a shared connection pool, each call to {@link #getClient()}
 * returns a <em>new</em> {@code HttpClient} configured with the same settings (base URL,
 * proxy, timeout, optional SSL context).  Callers are therefore responsible for reusing
 * the returned client appropriately for their use-case.</p>
 *
 * <p>Because no long-lived connection is held, this connector does <em>not</em> implement
 * {@link io.vertigo.core.node.component.Activeable}: there is nothing to start or stop.</p>
 *
 * @author npiedeloup
 */
public class HttpClientConnector implements Connector<HttpClient> {

	private static final int DEFAULT_CONNECT_TIMEOUT = 20; //20 seconds
	private final String connectionName;
	private final Optional<ProxySelector> proxyOpt;
	private final String urlPrefix;
	private final int connectTimeout;
	private Optional<SSLContext> sslContextOpt;

	@Inject
	public HttpClientConnector(
			@ParamValue("name") final Optional<String> connectionNameOpt,
			@ParamValue("urlPrefix") final String urlPrefix,
			@ParamValue("connectTimeoutSecond") final Optional<Integer> connectTimeoutOpt,
			@ParamValue("proxy") final Optional<String> proxyHostOpt,
			@ParamValue("proxyPort") final Optional<Integer> proxyPortOpt,
			@ParamValue("trustStoreUrl") final Optional<String> trustStoreUrlOpt,
			@ParamValue("trustStorePassword") final Optional<String> trustStorePasswordOpt,
			final ResourceManager resourceManager) {
		Assertion.check()
				.isNotBlank(urlPrefix)
				.isTrue(urlPrefix.startsWith("http"), "urlPrefix ({0}) must include protocol http or https", urlPrefix)
				.isFalse(urlPrefix.endsWith("/"), "urlPrefix ({0}) mustn't end with /", urlPrefix)
				.when(proxyHostOpt.isPresent(),
						() -> Assertion.check().isTrue(proxyPortOpt.isPresent(), "ProxyPort is mandatory if proxy was set"));
		//---
		connectionName = connectionNameOpt.orElse("main");
		this.urlPrefix = urlPrefix;
		connectTimeout = connectTimeoutOpt.orElse(DEFAULT_CONNECT_TIMEOUT);
		proxyOpt = proxyHostOpt.map(proxy -> ProxySelector.of(new InetSocketAddress(proxy, proxyPortOpt.get())));

		if (trustStoreUrlOpt.isPresent()) {
			sslContextOpt = Optional.of(ConnectorSslUtil.buildSslContext(resourceManager.resolve(trustStoreUrlOpt.get()), trustStorePasswordOpt.orElse(null)));
		} else {
			sslContextOpt = Optional.empty();
		}
	}

	@Override
	public HttpClient getClient() {
		final Builder builder = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.followRedirects(Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(connectTimeout));

		HttpClientCookie.getCurrentCookieManager()
				.ifPresent((cookieManager) -> builder.cookieHandler(cookieManager));

		proxyOpt.ifPresent((proxy) -> builder.proxy(proxy));
		sslContextOpt.ifPresent(builder::sslContext);
		return builder.build();
	}

	public String getUrlPrefix() {
		return urlPrefix;
	}

	@Override
	public String getName() {
		return connectionName;
	}

}
