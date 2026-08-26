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
package io.vertigo.connectors.httpclient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

/**
 * Connector that acts as a factory for {@link java.net.http.HttpClient} instances.
 *
 * <p>Supports server authentication through a custom trust store, and client authentication (mTLS) through a
 * key store from which a single, explicitly named entry is used as the client certificate.</p>
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

	private static final Logger LOGGER = LogManager.getLogger(HttpClientConnector.class);

	private static final int DEFAULT_CONNECT_TIMEOUT = 20; //20 seconds

	/** Separator for multi-valued params, consistent with the other Vertigo connectors (redis, elasticsearch). */
	private static final String PARAM_SEPARATOR = ";";

	/**
	 * Pinned KeyManagerFactory algorithm : the alias namespace exposed by a KeyManager is implementation
	 * dependent (SunX509 returns the raw keystore alias, NewSunX509 prefixes it), and the default algorithm is
	 * driven by the ssl.KeyManagerFactory.algorithm security property. Pinning it keeps the client alias
	 * selection independent from the JVM configuration.
	 */
	private static final String KEY_MANAGER_ALGORITHM = "SunX509";

	/**
	 * Minimum TLS version enforced by this connector, whatever its configuration.
	 * Defense in depth : some JVMs still allow obsolete versions, and an operator relaxing
	 * jdk.tls.disabledAlgorithms for a single legacy partner relaxes it for the whole JVM.
	 * To be raised over the Vertigo releases.
	 */
	private static final String MIN_TLS_VERSION = "TLSv1.2";

	/** Known TLS/SSL versions, from the oldest to the newest. An unknown name is treated as newer. */
	private static final List<String> TLS_VERSION_ORDER = List.of(
			"SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3");

	/** Remaining validity below which the client certificate expiration is logged as a warning at startup. */
	private static final Duration CERTIFICATE_EXPIRATION_WARNING = Duration.ofDays(30);

	private final String connectionName;
	private final Optional<ProxySelector> proxyOpt;
	private final String urlPrefix;
	private final int connectTimeout;
	private final Optional<SSLContext> sslContextOpt;
	private final Optional<String[]> tlsProtocolsOpt;
	private final Optional<String[]> tlsCipherSuitesOpt;

	@Inject
	public HttpClientConnector(
			@ParamValue("name") final Optional<String> connectionNameOpt,
			@ParamValue("urlPrefix") final String urlPrefix,
			@ParamValue("connectTimeoutSecond") final Optional<Integer> connectTimeoutOpt,
			@ParamValue("proxy") final Optional<String> proxyHostOpt,
			@ParamValue("proxyPort") final Optional<Integer> proxyPortOpt,
			@ParamValue("trustStoreUrl") final Optional<String> trustStoreUrlOpt,
			@ParamValue("trustStorePassword") final Optional<String> trustStorePasswordOpt,
			@ParamValue("keyStoreUrl") final Optional<String> keyStoreUrlOpt,
			@ParamValue("keyStorePassword") final Optional<String> keyStorePasswordOpt,
			@ParamValue("keyStoreKeyAlias") final Optional<String> keyStoreKeyAliasOpt,
			@ParamValue("keyStoreForceAlias") final Optional<Boolean> keyStoreForceAliasOpt,
			@ParamValue("tlsProtocols") final Optional<String> tlsProtocolsStrOpt,
			@ParamValue("tlsCipherSuites") final Optional<String> tlsCipherSuitesStrOpt,
			final ResourceManager resourceManager) {
		Assertion.check()
				.isNotBlank(urlPrefix)
				.isTrue(urlPrefix.startsWith("http"), "urlPrefix ({0}) must include protocol http or https", urlPrefix)
				.isFalse(urlPrefix.endsWith("/"), "urlPrefix ({0}) mustn't end with /", urlPrefix)
				.when(proxyHostOpt.isPresent(),
						() -> Assertion.check().isTrue(proxyPortOpt.isPresent(), "ProxyPort is mandatory if proxy was set"))
				.when(keyStoreUrlOpt.isPresent(),
						() -> Assertion.check()
								.isTrue(keyStoreKeyAliasOpt.isPresent(), "keyStoreKeyAlias is mandatory if keyStoreUrl was set")
								.isTrue(keyStorePasswordOpt.filter(password -> !password.isBlank()).isPresent(),
										"keyStorePassword is mandatory and mustn't be blank if keyStoreUrl was set"))
				.when(keyStoreForceAliasOpt.orElse(false),
						() -> Assertion.check().isTrue(keyStoreUrlOpt.isPresent(), "keyStoreForceAlias requires keyStoreUrl"));
		//---
		connectionName = connectionNameOpt.orElse("main");
		this.urlPrefix = urlPrefix;
		connectTimeout = connectTimeoutOpt.orElse(DEFAULT_CONNECT_TIMEOUT);
		proxyOpt = proxyHostOpt.map(proxy -> ProxySelector.of(new InetSocketAddress(proxy, proxyPortOpt.get())));
		tlsProtocolsOpt = tlsProtocolsStrOpt.map(value -> splitParam("tlsProtocols", value));
		tlsCipherSuitesOpt = tlsCipherSuitesStrOpt.map(value -> splitParam("tlsCipherSuites", value));

		if (trustStoreUrlOpt.isPresent() || keyStoreUrlOpt.isPresent()) {
			try {
				sslContextOpt = Optional.of(createSslContext(connectionName, resourceManager,
						trustStoreUrlOpt, trustStorePasswordOpt,
						keyStoreUrlOpt, keyStorePasswordOpt, keyStoreKeyAliasOpt, keyStoreForceAliasOpt.orElse(false)));
			} catch (final Exception e) {
				throw WrappedException.wrap(e);
			}
		} else {
			sslContextOpt = Optional.empty();
		}
		checkTlsParams();
		LOGGER.info("httpclient connector '{}' : min TLS version={}, protocols={}, cipher suites={}",
				connectionName, MIN_TLS_VERSION,
				tlsProtocolsOpt.map(Arrays::toString).orElse("(JVM defaults)"),
				tlsCipherSuitesOpt.map(Arrays::toString).orElse("(JVM defaults)"));
	}

	@Override
	public HttpClient getClient() {
		final Builder builder = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.followRedirects(Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(connectTimeout))
				.sslParameters(buildSslParameters());

		HttpClientCookie.getCurrentCookieManager()
				.ifPresent(builder::cookieHandler);

		proxyOpt.ifPresent(builder::proxy);
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

	/**
	 * TLS parameters of the client : context defaults, then the configured restrictions, then the version floor.
	 *
	 * Recomputed on each call, as HttpClient itself did before : a library replacing the JVM default SSLContext
	 * after the Vertigo startup would otherwise be ignored. The cost (two arrays and one object) is negligible
	 * compared to the TLS handshake that follows.
	 */
	private SSLParameters buildSslParameters() {
		final var sslParameters = resolveSslContext().getDefaultSSLParameters();
		tlsProtocolsOpt.ifPresent(sslParameters::setProtocols);
		tlsCipherSuitesOpt.ifPresent(sslParameters::setCipherSuites);
		//applied last : the floor cannot relax the JVM policy. It cannot be bypassed by the configuration either,
		//but that part is enforced by checkNoProtocolBelowMinVersion : see its javadoc, the handshake time check
		//never sees SSLv2Hello
		sslParameters.setAlgorithmConstraints(new MinTlsVersionConstraints(sslParameters.getAlgorithmConstraints()));
		return sslParameters;
	}

	private SSLContext resolveSslContext() {
		return sslContextOpt.orElseGet(() -> {
			try {
				return SSLContext.getDefault(); //the one HttpClient would use by itself
			} catch (final NoSuchAlgorithmException e) {
				throw WrappedException.wrap(e);
			}
		});
	}

	/**
	 * Splits a multi-valued param, rejecting an empty list : an environment variable that is defined but empty (or
	 * reduced to separators) would give an empty array, hence a setProtocols(new String[0]), hence a
	 * "No appropriate protocol" failure on every request, with nothing reported at startup. Failing to start is the
	 * better answer : falling back to the JVM defaults must stay an explicit choice, not the side effect of an
	 * empty variable.
	 */
	private String[] splitParam(final String paramName, final String value) {
		final var elements = Arrays.stream(value.split(PARAM_SEPARATOR))
				.map(String::trim)
				.filter(element -> !element.isEmpty())
				.toArray(String[]::new);
		Assertion.check()
				.isTrue(elements.length > 0,
						"Param {0} of connector '{1}' is set but holds no value : remove it from the configuration to "
								+ "use the JVM defaults",
						paramName, connectionName);
		return elements;
	}

	/** Validates the configured TLS params at startup, so that a mistake is not discovered on the first request. */
	private void checkTlsParams() {
		if (tlsProtocolsOpt.isEmpty() && tlsCipherSuitesOpt.isEmpty()) {
			return; //nothing to validate : no need to resolve the JVM default SSLContext at startup
		}
		//the floor is checked first : its message stays accurate whatever the versions this JVM still supports
		tlsProtocolsOpt.ifPresent(this::checkNoProtocolBelowMinVersion);
		final var supported = resolveSslContext().getSupportedSSLParameters();
		tlsProtocolsOpt.ifPresent(protocols -> checkAllSupported("tlsProtocols", protocols, supported.getProtocols()));
		tlsCipherSuitesOpt.ifPresent(suites -> checkAllSupported("tlsCipherSuites", suites, supported.getCipherSuites()));
	}

	/**
	 * Rejects at startup any configured version below the floor, which the handshake time check cannot guarantee on
	 * its own : HandshakeContext.getActiveProtocols() takes SSLv2Hello out of the loop before calling permits(),
	 * then adds it back unconditionally. A tlsProtocols holding SSLv2Hello would therefore keep that obsolete hello
	 * format despite the floor - without weakening the negotiated version, but many servers and network appliances
	 * reject an SSLv2 formatted ClientHello.
	 *
	 * Failing at startup is the right answer anyway : a version below the floor in the configuration is an operator
	 * mistake, better not to start than to silently filter it out on every request.
	 */
	private void checkNoProtocolBelowMinVersion(final String[] protocols) {
		final var belowMinVersion = Arrays.stream(protocols)
				.filter(HttpClientConnector::isBelowMinVersion)
				.toList();
		Assertion.check()
				.isTrue(belowMinVersion.isEmpty(),
						"Param tlsProtocols of connector '{0}' holds versions below the {1} floor enforced by this "
								+ "connector : {2}",
						connectionName, MIN_TLS_VERSION, belowMinVersion);
	}

	/**
	 * True for the known versions older than the floor only. A name absent from TLS_VERSION_ORDER is treated as
	 * newer : that is required at handshake time, where permits() is also called for cipher suites and signature
	 * algorithms, which would otherwise all be rejected.
	 */
	private static boolean isBelowMinVersion(final String algorithm) {
		final var index = TLS_VERSION_ORDER.indexOf(algorithm);
		return index >= 0 && index < TLS_VERSION_ORDER.indexOf(MIN_TLS_VERSION);
	}

	/**
	 * Validates the protocol and cipher suite names at startup : SSLParameters.setProtocols and setCipherSuites
	 * check nothing, and the JDK only rejects an unknown name in SSLEngine.setSSLParameters, that is at connection
	 * time. Without this check a typo passes the startup - which even logs the value as if it were accepted - and
	 * then breaks every outgoing call of the connector.
	 */
	private void checkAllSupported(final String paramName, final String[] configured, final String[] supported) {
		final var supportedNames = Set.copyOf(Arrays.asList(supported));
		final var unknown = Arrays.stream(configured)
				.filter(element -> !supportedNames.contains(element))
				.toList();
		Assertion.check()
				.isTrue(unknown.isEmpty(),
						"Param {0} of connector '{1}' holds values unknown to this JVM : {2}",
						paramName, connectionName, unknown);
	}

	/**
	 * Builds an SSLContext, optionally configured with :
	 * - a custom trust store (to trust a specific/private CA), and/or
	 * - a single client certificate designated by its alias (for mTLS).
	 *
	 * Both parts are independent : without trustStoreUrl the JVM default trust managers are used,
	 * without keyStoreUrl no client certificate is presented (plain TLS, no mTLS).
	 */
	private static SSLContext createSslContext(final String connectionName, final ResourceManager resourceManager,
			final Optional<String> trustStoreUrlOpt, final Optional<String> trustStorePasswordOpt,
			final Optional<String> keyStoreUrlOpt, final Optional<String> keyStorePasswordOpt,
			final Optional<String> keyStoreKeyAliasOpt, final boolean forceAlias)
			throws GeneralSecurityException, IOException {

		TrustManager[] trustManagers = null; // null => JVM default trust managers
		if (trustStoreUrlOpt.isPresent()) {
			//password is optional : a null one skips the integrity check, which allows reading certificates only
			final var trustStore = loadKeyStore(resourceManager.resolve(trustStoreUrlOpt.get()),
					trustStorePasswordOpt.map(String::toCharArray).orElse(null));
			final var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(trustStore);
			trustManagers = trustManagerFactory.getTrustManagers();
		}

		KeyManager[] keyManagers = null; // null => no client certificate presented
		if (keyStoreUrlOpt.isPresent()) {
			//both guaranteed by the Assertions of the constructor
			final var keyPassword = keyStorePasswordOpt.orElseThrow().toCharArray();
			final var alias = keyStoreKeyAliasOpt.orElseThrow();
			final var keyStore = loadKeyStore(resourceManager.resolve(keyStoreUrlOpt.get()), keyPassword);
			keyManagers = createKeyManagers(connectionName, keyStore, keyPassword, alias, forceAlias);
		}

		final var sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers, trustManagers, new SecureRandom());
		return sslContext;
	}

	private static KeyStore loadKeyStore(final URL storeUrl, final char[] password)
			throws GeneralSecurityException, IOException {
		final var keyStore = KeyStore.getInstance("pkcs12");
		try (var inputStream = storeUrl.openStream()) {
			keyStore.load(inputStream, password);
		}
		return keyStore;
	}

	/**
	 * Builds the KeyManagers for the single client certificate designated by its alias.
	 *
	 * The source key store may hold other private keys (SAML signature, SFTP...) and trusted certificates : the
	 * wanted entry only is copied into an in-memory single-entry key store. The KeyManager then structurally has
	 * a single choice, and no other private key of the application is exposed to it.
	 *
	 * When forceAlias is true, the client alias selection is in addition forced on that entry, which bypasses
	 * the certificate authority filtering requested by the server in its CertificateRequest. That filtering is a
	 * SHOULD of the TLS specification, not a MUST : forcing helps against a server sending a truncated CA list,
	 * at the cost of sending a certificate the server did not ask for (it then answers certificate_unknown,
	 * an actionable diagnostic, rather than the misleading certificate_required).
	 */
	private static KeyManager[] createKeyManagers(final String connectionName, final KeyStore keyStore,
			final char[] keyPassword, final String alias, final boolean forceAlias)
			throws GeneralSecurityException, IOException {
		Assertion.check()
				.isTrue(keyStore.isKeyEntry(alias),
						"Alias '{0}' is not a private key entry of the keyStore of connector '{1}'", alias, connectionName);
		//---
		//isKeyEntry is also true for a secret key entry : getCertificate would then return null
		final var keyEntryCertificate = keyStore.getCertificate(alias);
		Assertion.check()
				.isNotNull(keyEntryCertificate,
						"Alias '{0}' of the keyStore of connector '{1}' exposes no certificate : it most likely "
								+ "designates a secret key entry, not a private key with its chain",
						alias, connectionName)
				.isTrue(keyEntryCertificate instanceof X509Certificate,
						"The certificate of alias '{0}' of the keyStore of connector '{1}' is not an X509 one but a {2}",
						alias, connectionName, keyEntryCertificate.getClass().getSimpleName());
		//---
		final var certificate = (X509Certificate) keyEntryCertificate;
		final var singleEntryStore = KeyStore.getInstance("pkcs12");
		singleEntryStore.load(null, null);
		singleEntryStore.setKeyEntry(alias, keyStore.getKey(alias, keyPassword), keyPassword,
				keyStore.getCertificateChain(alias));

		final var keyManagerFactory = KeyManagerFactory.getInstance(KEY_MANAGER_ALGORITHM);
		keyManagerFactory.init(singleEntryStore, keyPassword);
		final var keyManagers = keyManagerFactory.getKeyManagers();
		Assertion.check()
				.isTrue(keyManagers.length == 1 && keyManagers[0] instanceof X509ExtendedKeyManager,
						"{0} did not provide the single expected X509ExtendedKeyManager", KEY_MANAGER_ALGORITHM);
		//---
		final var delegate = (X509ExtendedKeyManager) keyManagers[0];
		//the alias as the KeyManager names it : the key store holds a single entry, so there is only one
		final var keyAlgorithm = certificate.getPublicKey().getAlgorithm();
		final var internalAliases = delegate.getClientAliases(keyAlgorithm, null);
		Assertion.check()
				.isNotNull(internalAliases, "No usable {0} key for alias '{1}'", keyAlgorithm, alias)
				.isTrue(internalAliases.length == 1,
						"The single-entry keyStore exposes {0} aliases instead of one", internalAliases.length)
				//defensive : structurally impossible today, as the single-entry key store was just built with that
				//key. The assertion catches a future change of the KeyManagers construction.
				.isNotNull(delegate.getPrivateKey(internalAliases[0]),
						"KeyManager {0} exposes no private key for its own internal alias '{1}' (configured alias : "
								+ "'{2}') although the single-entry keyStore was just built with that key : internal "
								+ "inconsistency, the KeyManagers construction must have changed",
						KEY_MANAGER_ALGORITHM, internalAliases[0], alias);
		//---
		logClientCertificate(connectionName, alias, certificate, forceAlias);
		return forceAlias
				? new KeyManager[] { new AliasForcingKeyManager(delegate, internalAliases[0]) }
				: keyManagers;
	}

	private static void logClientCertificate(final String connectionName, final String alias,
			final X509Certificate certificate, final boolean forceAlias) {
		final var notBefore = certificate.getNotBefore().toInstant();
		final var notAfter = certificate.getNotAfter().toInstant();
		LOGGER.info("mTLS enabled on connector '{}' : alias='{}', subject='{}', issuer='{}', valid from {} to {} (forceAlias={})",
				connectionName, alias, certificate.getSubjectX500Principal(), certificate.getIssuerX500Principal(),
				notBefore, notAfter, forceAlias);
		//checked once at startup : a long running instance will not be warned again
		final var now = Instant.now();
		if (now.isBefore(notBefore)) {
			LOGGER.warn("Client certificate of connector '{}' (alias '{}') is not valid yet (valid from {}) : the "
					+ "server will reject the handshakes until then, check the clock of this machine",
					connectionName, alias, notBefore);
		} else if (now.isAfter(notAfter)) {
			LOGGER.warn("Client certificate of connector '{}' (alias '{}') expired on {} : the server will reject the "
					+ "handshakes, renew it now",
					connectionName, alias, notAfter);
		} else if (notAfter.isBefore(now.plus(CERTIFICATE_EXPIRATION_WARNING))) {
			LOGGER.warn("Client certificate of connector '{}' (alias '{}') expires on {} : plan its renewal",
					connectionName, alias, notAfter);
		}
	}

	/**
	 * Rejects any TLS version older than {@link #MIN_TLS_VERSION}, through the very mechanism used by the
	 * jdk.tls.disabledAlgorithms security property. The handshake then fails with the usual
	 * "No appropriate protocol" message, rather than with a misleading certificate error.
	 *
	 * Everything else is delegated : this constraint can only harden the JVM policy, never relax it.
	 * Stateless, hence safely called concurrently by every handshake.
	 */
	private static final class MinTlsVersionConstraints implements AlgorithmConstraints {

		private final AlgorithmConstraints delegate;

		MinTlsVersionConstraints(final AlgorithmConstraints delegate) {
			this.delegate = delegate;
		}

		//isBelowMinVersion is the connector one : the startup refusal and the handshake time refusal deliberately
		//share a single definition of the floor
		@Override
		public boolean permits(final Set<CryptoPrimitive> primitives, final String algorithm,
				final AlgorithmParameters parameters) {
			return !isBelowMinVersion(algorithm)
					&& (delegate == null || delegate.permits(primitives, algorithm, parameters));
		}

		@Override
		public boolean permits(final Set<CryptoPrimitive> primitives, final Key key) {
			return delegate == null || delegate.permits(primitives, key);
		}

		@Override
		public boolean permits(final Set<CryptoPrimitive> primitives, final String algorithm, final Key key,
				final AlgorithmParameters parameters) {
			return !isBelowMinVersion(algorithm)
					&& (delegate == null || delegate.permits(primitives, algorithm, key, parameters));
		}
	}

	/**
	 * Delegating X509ExtendedKeyManager that always answers the configured alias for client-side alias selection
	 * (both the legacy Socket based handshake and the SSLEngine based one used by java.net.http.HttpClient),
	 * while delegating certificate chain and private key lookups, as well as all the server-side methods
	 * (unused here), to the real manager produced by the KeyManagerFactory.
	 *
	 * The forced alias is the one the delegate exposes itself, not the string read from the configuration :
	 * the alias namespace depends on the KeyManagerFactory implementation.
	 */
	private static final class AliasForcingKeyManager extends X509ExtendedKeyManager {

		private final X509ExtendedKeyManager delegate;
		private final String forcedAlias;

		AliasForcingKeyManager(final X509ExtendedKeyManager delegate, final String forcedAlias) {
			this.delegate = delegate;
			this.forcedAlias = forcedAlias;
		}

		@Override
		public String chooseClientAlias(final String[] keyType, final Principal[] issuers, final Socket socket) {
			return forcedAlias;
		}

		@Override
		public String chooseEngineClientAlias(final String[] keyType, final Principal[] issuers,
				final SSLEngine engine) {
			return forcedAlias;
		}

		@Override
		public X509Certificate[] getCertificateChain(final String alias) {
			return delegate.getCertificateChain(alias);
		}

		@Override
		public PrivateKey getPrivateKey(final String alias) {
			return delegate.getPrivateKey(alias);
		}

		@Override
		public String[] getClientAliases(final String keyType, final Principal[] issuers) {
			return delegate.getClientAliases(keyType, issuers);
		}

		// --- Server-side methods : unused for an HTTP client, delegated for completeness ---

		@Override
		public String chooseServerAlias(final String keyType, final Principal[] issuers, final Socket socket) {
			return delegate.chooseServerAlias(keyType, issuers, socket);
		}

		@Override
		public String chooseEngineServerAlias(final String keyType, final Principal[] issuers, final SSLEngine engine) {
			return delegate.chooseEngineServerAlias(keyType, issuers, engine);
		}

		@Override
		public String[] getServerAliases(final String keyType, final Principal[] issuers) {
			return delegate.getServerAliases(keyType, issuers);
		}
	}

}
