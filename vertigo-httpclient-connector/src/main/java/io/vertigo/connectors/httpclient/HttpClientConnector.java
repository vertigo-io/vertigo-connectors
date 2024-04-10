/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2023, Vertigo.io, team@vertigo.io
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

/**
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
			try {
				sslContextOpt = Optional.of(createTrustStoreSslContext(resourceManager.resolve(trustStoreUrlOpt.get()), trustStorePasswordOpt.orElseGet(() -> null)));
			} catch (final Exception e) {
				throw WrappedException.wrap(e);
			}
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

	private static SSLContext createTrustStoreSslContext(final URL trustStoreUrl, final String trustStorePassword) throws GeneralSecurityException, IOException {
		final var trustStore = KeyStore.getInstance("pkcs12");
		try (var inputStream = trustStoreUrl.openStream()) {
			trustStore.load(inputStream, trustStorePassword.toCharArray());
		}

		final var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);
		final var trustManagers = trustManagerFactory.getTrustManagers();
		final var sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(null, trustManagers, new SecureRandom());
		return sslContext;
	}

}
