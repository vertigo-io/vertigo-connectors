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
package io.vertigo.connectors.ssl;

import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;

/**
 * Shared SSL/TLS utility for Vertigo connectors.
 *
 * <p>Eliminates duplication of trust-store loading across connector modules
 * (Redis, Elasticsearch, HttpClient, …).  All methods load a PKCS12 trust store
 * and build a TLSv1.2 {@link SSLContext}.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * // SSLContext (for HttpClient, Elasticsearch REST …)
 * SSLContext ctx = ConnectorSslUtil.buildSslContext(trustStoreUrl, password);
 *
 * // SSLSocketFactory (for Jedis …)
 * SSLSocketFactory sf = ConnectorSslUtil.buildSslSocketFactory(trustStoreUrl, password);
 * </pre>
 *
 * @author pchretien
 */
public final class ConnectorSslUtil {

	private ConnectorSslUtil() {
		// static utility
	}

	/**
	 * Builds an {@link SSLContext} from a PKCS12 trust store.
	 *
	 * @param trustStoreUrl      URL of the PKCS12 key store (classpath or file)
	 * @param trustStorePassword password of the trust store
	 * @return a TLSv1.2 {@link SSLContext} initialised with the provided trust store
	 * @throws io.vertigo.core.lang.WrappedException on any security or IO error
	 */
	public static SSLContext buildSslContext(final URL trustStoreUrl, final String trustStorePassword) {
		Assertion.check()
				.isNotNull(trustStoreUrl)
				.isNotNull(trustStorePassword);
		//---
		try {
			final var trustStore = KeyStore.getInstance("pkcs12");
			try (var inputStream = trustStoreUrl.openStream()) {
				trustStore.load(inputStream, trustStorePassword.toCharArray());
			}
			final var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(trustStore);
			final var sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
			return sslContext;
		} catch (final Exception e) {
			throw WrappedException.wrap(e, "Failed to build SSLContext from trust store: {0}", trustStoreUrl);
		}
	}

	/**
	 * Builds an {@link SSLSocketFactory} from a PKCS12 trust store.
	 * Convenience method for clients that require a socket factory (e.g. Jedis).
	 *
	 * @param trustStoreUrl      URL of the PKCS12 key store (classpath or file)
	 * @param trustStorePassword password of the trust store
	 * @return an {@link SSLSocketFactory} derived from a TLSv1.2 {@link SSLContext}
	 */
	public static SSLSocketFactory buildSslSocketFactory(final URL trustStoreUrl, final String trustStorePassword) {
		Assertion.check()
				.isNotNull(trustStoreUrl)
				.isNotNull(trustStorePassword);
		//---
		return buildSslContext(trustStoreUrl, trustStorePassword).getSocketFactory();
	}
}
