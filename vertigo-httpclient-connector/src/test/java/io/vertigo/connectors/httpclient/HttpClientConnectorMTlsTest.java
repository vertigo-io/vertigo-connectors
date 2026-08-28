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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CryptoPrimitive;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import io.vertigo.core.resource.ResourceManager;

/**
 * Tests of the TLS/mTLS support of the HttpClientConnector.
 *
 * Fully self-contained : the key material is generated at test time with the keytool of the running JDK, and the
 * peer is a JDK HttpsServer bound on a random loopback port. No network access, no external service.
 *
 * @author npiedeloup
 */
public class HttpClientConnectorMTlsTest {

	private static final String PASSWORD = "changeit";
	private static final String CLIENT_ALIAS = "client";
	private static final String OTHER_ALIAS = "other";
	private static final String SERVER_CERT_ALIAS = "servercert";
	private static final String SECRET_ALIAS = "secret";

	@TempDir
	private static Path tempDir;

	/**
	 * Shared store, as usually deployed : it holds the client private key, another unrelated private key, and the
	 * certificate to trust. It is used both as trust store and as key store.
	 */
	private static Path sharedStore;
	private static Path serverStore;
	private static Path serverTrustStore;

	/** Stores holding a single client key, with an unusable certificate or an unusual entry type. */
	private static Path secretKeyStore;
	private static Path notYetValidStore;
	private static Path expiredStore;
	private static Path expiringSoonStore;

	private static HttpsServer server;
	private static URI serverUri;

	/**
	 * Subject of the client certificate the server was last given, or null if it was given none. Set by the server
	 * trust managers, read by the test that triggered the call : the tests run sequentially.
	 */
	private static final AtomicReference<String> clientPrincipalSeenByServer = new AtomicReference<>();

	/** Resolves a resource from its absolute file path. */
	private static final ResourceManager RESOURCE_MANAGER = new ResourceManager() {
		@Override
		public URL resolve(final String resource) {
			try {
				return Path.of(resource).toUri().toURL();
			} catch (final IOException e) {
				throw new IllegalStateException(e);
			}
		}
	};

	@BeforeAll
	static void setUp() throws Exception {
		generateKeyMaterial();
		startServer();
	}

	@AfterAll
	static void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// mTLS handshake
	// ------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("The client certificate is presented, with the alias selection forced")
	void clientCertificateIsPresentedWithForcedAlias() throws Exception {
		assertEquals("CN=client", call(connector(CLIENT_ALIAS, PASSWORD, true, null)));
	}

	@Test
	@DisplayName("The client certificate is presented, letting JSSE select it")
	void clientCertificateIsPresentedWithoutForcedAlias() throws Exception {
		assertEquals("CN=client", call(connector(CLIENT_ALIAS, PASSWORD, null, null)));
	}

	@Test
	@DisplayName("The alias is matched case insensitively, as a PKCS12 lookup is")
	void aliasIsCaseInsensitive() throws Exception {
		assertEquals("CN=client", call(connector(CLIENT_ALIAS.toUpperCase(), PASSWORD, true, null)));
	}

	/**
	 * The rejection tests pin TLSv1.2, because the shape of the failure is not portable over TLS 1.3 : the client
	 * finishes its handshake before the server has verified the client certificate, so the alert may only surface
	 * while reading the response. Up to JDK 21 that is an IOException("HTTP/1.1 header parser received no bytes")
	 * caused by a connection reset, while JDK 25 reports an SSLHandshakeException. Over TLS 1.2 the certificate is
	 * part of the handshake, so every JDK fails the same way, and the alert name is readable in the message.
	 * The default (TLS 1.3) path is covered by rejectionIsReportedOverTheDefaultProtocols below.
	 */
	@Test
	@DisplayName("Without a key store no client certificate is sent, and the server rejects the handshake")
	void withoutKeyStoreNoClientCertificateIsSent() {
		clientPrincipalSeenByServer.set(null);
		assertThrows(SSLHandshakeException.class, () -> call(connectorWithoutKeyStore("TLSv1.2")));
		assertNull(clientPrincipalSeenByServer.get(), "no client certificate should have reached the server");
	}

	@Test
	@DisplayName("Forcing the alias sends a certificate the server did not ask for, instead of sending none")
	void forcedAliasBypassesTheCertificateAuthorityFiltering() {
		//the server does not trust OTHER_ALIAS and does not list its issuer in the CertificateRequest : forcing
		//sends it anyway, so the server rejects a certificate it received (certificate_unknown) instead of
		//complaining that none was provided (certificate_required). The alert name is not readable from the client
		//on every JDK, so what the server was given is asserted instead.
		clientPrincipalSeenByServer.set(null);
		assertThrows(SSLHandshakeException.class, () -> call(connector(OTHER_ALIAS, PASSWORD, true, "TLSv1.2")));
		assertEquals("CN=" + OTHER_ALIAS, clientPrincipalSeenByServer.get(),
				"the forced alias must have been presented although the server did not ask for it");
	}

	@Test
	@DisplayName("Over the default protocols the rejection is still reported, whatever its shape")
	void rejectionIsReportedOverTheDefaultProtocols() {
		//TLS 1.3 by default : only the IOException family is portable here, see the note above. SSLHandshakeException
		//being an IOException, this assertion holds on every JDK while still proving the call cannot succeed.
		assertThrows(IOException.class, () -> call(connectorWithoutKeyStore(null)));
	}

	// ------------------------------------------------------------------------------------------------
	// Fail fast at startup
	// ------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("An unknown alias is rejected at startup")
	void unknownAliasIsRejectedAtStartup() {
		assertStartupFailure("is not a private key entry", () -> connector("unknown", PASSWORD, true, null));
	}

	@Test
	@DisplayName("An alias pointing to a trusted certificate entry is rejected at startup")
	void trustedCertificateAliasIsRejectedAtStartup() {
		assertStartupFailure("is not a private key entry", () -> connector(SERVER_CERT_ALIAS, PASSWORD, true, null));
	}

	@Test
	@DisplayName("A missing key store password is rejected at startup")
	void missingKeyStorePasswordIsRejectedAtStartup() {
		assertStartupFailure("keyStorePassword is mandatory", () -> connector(CLIENT_ALIAS, null, true, null));
	}

	@Test
	@DisplayName("A blank key store password is rejected at startup")
	void blankKeyStorePasswordIsRejectedAtStartup() {
		assertStartupFailure("keyStorePassword is mandatory", () -> connector(CLIENT_ALIAS, "   ", true, null));
	}

	@Test
	@DisplayName("A missing alias is rejected at startup")
	void missingAliasIsRejectedAtStartup() {
		assertStartupFailure("keyStoreKeyAlias is mandatory", () -> connector(null, PASSWORD, true, null));
	}

	@Test
	@DisplayName("Forcing the alias without a key store is rejected at startup")
	void forceAliasWithoutKeyStoreIsRejectedAtStartup() {
		assertStartupFailure("keyStoreForceAlias requires keyStoreUrl",
				() -> new HttpClientConnector(Optional.of("test"), "https://localhost", Optional.empty(),
						Optional.empty(), Optional.empty(),
						Optional.of(sharedStore.toString()), Optional.of(PASSWORD),
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.of(true), Optional.empty(), Optional.empty(), RESOURCE_MANAGER));
	}

	// ------------------------------------------------------------------------------------------------
	// TLS parameters and version floor
	// ------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("The configured protocols restrict the negotiated version")
	void configuredProtocolsRestrictTheNegotiatedVersion() throws Exception {
		final var connector = connector(CLIENT_ALIAS, PASSWORD, true, "TLSv1.2");
		final var response = send(connector);
		assertEquals("TLSv1.2", response.sslSession().map(SSLSession::getProtocol).orElse(null));
	}

	@Test
	@DisplayName("Multi-valued params are split on ';' and trimmed")
	void multiValuedParamsAreSplitAndTrimmed() {
		final var connector = connector(CLIENT_ALIAS, PASSWORD, true, " TLSv1.3 ; TLSv1.2 ; ");
		assertArrayEquals(new String[] { "TLSv1.3", "TLSv1.2" },
				connector.getClient().sslParameters().getProtocols());
	}

	@Test
	@DisplayName("Every client carries the version floor of the connector")
	void everyClientCarriesTheVersionFloor() {
		final var constraints = connector(CLIENT_ALIAS, PASSWORD, true, null).getClient()
				.sslParameters().getAlgorithmConstraints();
		assertNotNull(constraints, "the connector must install its own version floor on every client");
		assertTrue(constraints.getClass().getName().contains("MinTlsVersionConstraints"),
				"expected the connector constraints, got : " + constraints.getClass().getName());
	}

	@Test
	@DisplayName("The version floor rejects the obsolete versions and only them")
	void theVersionFloorRejectsObsoleteVersionsOnly() {
		//note : a standard JVM already disables those versions through jdk.tls.disabledAlgorithms, so this test
		//asserts the contract of the constraint rather than an observable difference. What it does catch is an
		//over-rejection : the constraint is also asked about cipher suites and signature algorithms, and
		//rejecting those would silently break every handshake.
		final var constraints = connector(CLIENT_ALIAS, PASSWORD, true, null).getClient()
				.sslParameters().getAlgorithmConstraints();
		final Set<CryptoPrimitive> primitives = Set.of(CryptoPrimitive.KEY_AGREEMENT);

		assertFalse(constraints.permits(primitives, "SSLv3", null));
		assertFalse(constraints.permits(primitives, "TLSv1", null));
		assertFalse(constraints.permits(primitives, "TLSv1.1", null));

		assertTrue(constraints.permits(primitives, "TLSv1.2", null));
		assertTrue(constraints.permits(primitives, "TLSv1.3", null));
		assertTrue(constraints.permits(primitives, "TLS_AES_256_GCM_SHA384", null));
		assertTrue(constraints.permits(primitives, "rsa_pss_rsae_sha256", null));
	}

	// ------------------------------------------------------------------------------------------------
	// TLS params validated at startup
	// ------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("A protocols param set but holding no value is rejected at startup")
	void emptyProtocolsParamIsRejectedAtStartup() {
		//the realistic case is an environment variable that is defined but empty : without this check the param
		//would silently become an empty array, and every request would fail with "No appropriate protocol"
		assertStartupFailure("is set but holds no value", () -> connector(CLIENT_ALIAS, PASSWORD, true, " ; ; "));
	}

	@Test
	@DisplayName("A cipher suites param set but holding no value is rejected at startup")
	void emptyCipherSuitesParamIsRejectedAtStartup() {
		assertStartupFailure("is set but holds no value",
				() -> connector(sharedStore, CLIENT_ALIAS, PASSWORD, true, null, ""));
	}

	@Test
	@DisplayName("A protocol name unknown to the JVM is rejected at startup, not on the first request")
	void unknownProtocolIsRejectedAtStartup() {
		//"TLS1.2" instead of "TLSv1.2" : SSLParameters.setProtocols would accept it, and only
		//SSLEngine.setSSLParameters would reject it, at connection time
		assertStartupFailure("unknown to this JVM", () -> connector(CLIENT_ALIAS, PASSWORD, true, "TLS1.2"));
	}

	@Test
	@DisplayName("A cipher suite name unknown to the JVM is rejected at startup")
	void unknownCipherSuiteIsRejectedAtStartup() {
		assertStartupFailure("unknown to this JVM",
				() -> connector(sharedStore, CLIENT_ALIAS, PASSWORD, true, null, "TLS_NO_SUCH_SUITE"));
	}

	@Test
	@DisplayName("The configured cipher suites are carried by every client")
	void configuredCipherSuitesAreCarried() {
		final var connector = connector(sharedStore, CLIENT_ALIAS, PASSWORD, true, null, "TLS_AES_256_GCM_SHA384");
		assertArrayEquals(new String[] { "TLS_AES_256_GCM_SHA384" },
				connector.getClient().sslParameters().getCipherSuites());
	}

	@Test
	@DisplayName("A configured version below the floor is rejected at startup")
	void protocolBelowTheFloorIsRejectedAtStartup() {
		assertStartupFailure("below the TLSv1.2 floor",
				() -> connector(CLIENT_ALIAS, PASSWORD, true, "TLSv1.1;TLSv1.2"));
	}

	@Test
	@DisplayName("SSLv2Hello is rejected at startup, as the handshake time floor cannot see it")
	void sslV2HelloIsRejectedAtStartup() {
		//HandshakeContext.getActiveProtocols() takes SSLv2Hello out of the loop before calling permits(), then adds
		//it back unconditionally : MinTlsVersionConstraints is never asked about it. Refusing the configuration is
		//therefore the only way to keep that obsolete hello format out.
		assertStartupFailure("below the TLSv1.2 floor",
				() -> connector(CLIENT_ALIAS, PASSWORD, true, "SSLv2Hello;TLSv1.2"));
	}

	// ------------------------------------------------------------------------------------------------
	// Client certificate diagnostics
	// ------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("An alias pointing to a secret key entry is rejected at startup, without a raw NullPointerException")
	void secretKeyAliasIsRejectedAtStartup() {
		//isKeyEntry() is true for a secret key entry, but getCertificate() returns null : without the check the
		//startup would fail on a bare NPE instead of naming the offending alias
		assertStartupFailure("exposes no certificate",
				() -> connector(secretKeyStore, SECRET_ALIAS, PASSWORD, true, null, null));
	}

	@Test
	@DisplayName("A certificate that is not valid yet is warned about, and does not prevent the startup")
	void notYetValidCertificateIsWarnedAboutAtStartup() throws Throwable {
		final var warnings = captureWarnings(
				() -> connector(notYetValidStore, CLIENT_ALIAS, PASSWORD, true, null, null));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("is not valid yet")),
				"expected a 'not valid yet' warning, got : " + warnings);
	}

	@Test
	@DisplayName("An already expired certificate is warned about, and does not prevent the startup")
	void expiredCertificateIsWarnedAboutAtStartup() throws Throwable {
		final var warnings = captureWarnings(() -> connector(expiredStore, CLIENT_ALIAS, PASSWORD, true, null, null));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("expired on")),
				"expected an 'expired on' warning, got : " + warnings);
	}

	@Test
	@DisplayName("A certificate expiring soon is warned about")
	void expiringSoonCertificateIsWarnedAboutAtStartup() throws Throwable {
		final var warnings = captureWarnings(
				() -> connector(expiringSoonStore, CLIENT_ALIAS, PASSWORD, true, null, null));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("plan its renewal")),
				"expected a 'plan its renewal' warning, got : " + warnings);
	}

	@Test
	@DisplayName("A certificate valid for a long time is not warned about")
	void longLivedCertificateIsNotWarnedAbout() throws Throwable {
		final var warnings = captureWarnings(() -> connector(CLIENT_ALIAS, PASSWORD, true, null));
		assertTrue(warnings.isEmpty(), "expected no warning, got : " + warnings);
	}

	// ------------------------------------------------------------------------------------------------
	// Fixture
	// ------------------------------------------------------------------------------------------------

	private static HttpClientConnector connector(final String alias, final String password, final Boolean forceAlias,
			final String protocols) {
		return connector(sharedStore, alias, password, forceAlias, protocols, null);
	}

	/** Trust store only : no client certificate can be presented. */
	private static HttpClientConnector connectorWithoutKeyStore(final String protocols) {
		return new HttpClientConnector(Optional.of("test"), "https://localhost", Optional.empty(),
				Optional.empty(), Optional.empty(),
				Optional.of(sharedStore.toString()), Optional.of(PASSWORD),
				Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.ofNullable(protocols), Optional.empty(), RESOURCE_MANAGER);
	}

	private static HttpClientConnector connector(final Path keyStore, final String alias, final String password,
			final Boolean forceAlias, final String protocols, final String cipherSuites) {
		return new HttpClientConnector(Optional.of("test"), "https://localhost", Optional.empty(),
				Optional.empty(), Optional.empty(),
				Optional.of(sharedStore.toString()), Optional.of(PASSWORD),
				Optional.of(keyStore.toString()), Optional.ofNullable(password), Optional.ofNullable(alias),
				Optional.ofNullable(forceAlias), Optional.ofNullable(protocols), Optional.ofNullable(cipherSuites),
				RESOURCE_MANAGER);
	}

	/** Calls the test server and returns the principal it saw, or throws the handshake failure. */
	private static String call(final HttpClientConnector connector) throws Exception {
		return send(connector).body();
	}

	private static HttpResponse<String> send(final HttpClientConnector connector) throws Exception {
		return connector.getClient()
				.send(HttpRequest.newBuilder(serverUri).build(), HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * Collects the warnings logged by the connector while the executable runs. The level is raised for the duration
	 * of the capture, as the default log4j configuration of a test run only lets errors through.
	 */
	private static List<String> captureWarnings(final Executable executable) throws Throwable {
		final var warnings = new ArrayList<String>();
		final var appender = new AbstractAppender("captureWarnings", null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(final LogEvent event) {
				if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
					warnings.add(event.getMessage().getFormattedMessage());
				}
			}
		};
		appender.start();
		final var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(HttpClientConnector.class);
		final var previousLevel = logger.getLevel();
		Configurator.setLevel(HttpClientConnector.class, Level.WARN);
		logger.addAppender(appender);
		try {
			executable.execute();
		} finally {
			logger.removeAppender(appender);
			appender.stop();
			Configurator.setLevel(HttpClientConnector.class, previousLevel);
		}
		return warnings;
	}

	/** Asserts that the executable fails, with the expected fragment somewhere in the causes chain. */
	private static void assertStartupFailure(final String expectedMessageFragment, final Executable executable) {
		try {
			executable.execute();
		} catch (final Throwable thrown) {
			for (var cause = thrown; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
				if (cause.getMessage() != null && cause.getMessage().contains(expectedMessageFragment)) {
					return;
				}
			}
			fail("expected a failure mentioning '" + expectedMessageFragment + "', got : " + thrown);
		}
		fail("expected a failure mentioning '" + expectedMessageFragment + "', but none was raised");
	}

	// ------------------------------------------------------------------------------------------------
	// Key material and test server
	// ------------------------------------------------------------------------------------------------

	private static void generateKeyMaterial() throws Exception {
		sharedStore = tempDir.resolve("shared.p12");
		serverStore = tempDir.resolve("server.p12");
		serverTrustStore = tempDir.resolve("server-trust.p12");
		final var serverCert = tempDir.resolve("server.cer");
		final var clientCert = tempDir.resolve("client.cer");

		generateKeyPair(serverStore, "server", "CN=localhost", "SAN=dns:localhost,ip:127.0.0.1");
		generateKeyPair(sharedStore, CLIENT_ALIAS, "CN=" + CLIENT_ALIAS, null);
		generateKeyPair(sharedStore, OTHER_ALIAS, "CN=" + OTHER_ALIAS, null);

		//dedicated stores : kept out of the shared one so that they cannot disturb the handshake tests
		secretKeyStore = tempDir.resolve("secret.p12");
		notYetValidStore = tempDir.resolve("not-yet-valid.p12");
		expiredStore = tempDir.resolve("expired.p12");
		expiringSoonStore = tempDir.resolve("expiring-soon.p12");
		generateSecretKey(secretKeyStore, SECRET_ALIAS);
		generateKeyPair(notYetValidStore, CLIENT_ALIAS, "CN=notyetvalid", null, "+2d", 30);
		generateKeyPair(expiredStore, CLIENT_ALIAS, "CN=expired", null, "-10d", 5);
		generateKeyPair(expiringSoonStore, CLIENT_ALIAS, "CN=expiringsoon", null, null, 10);

		exportCertificate(serverStore, "server", serverCert);
		importCertificate(sharedStore, SERVER_CERT_ALIAS, serverCert);
		exportCertificate(sharedStore, CLIENT_ALIAS, clientCert);
		importCertificate(serverTrustStore, CLIENT_ALIAS, clientCert);
	}

	private static void generateKeyPair(final Path store, final String alias, final String dname, final String extension)
			throws Exception {
		generateKeyPair(store, alias, dname, extension, null, 3650);
	}

	/** startDate is a keytool offset such as "+2d" or "-10d", validityDays counts from that start date. */
	private static void generateKeyPair(final Path store, final String alias, final String dname,
			final String extension, final String startDate, final int validityDays) throws Exception {
		final var arguments = new ArrayList<>(List.of("-genkeypair", "-alias", alias, "-keyalg", "RSA",
				"-keysize", "2048", "-dname", dname, "-validity", String.valueOf(validityDays),
				"-storetype", "pkcs12", "-keystore", store.toString(), "-storepass", PASSWORD));
		if (extension != null) {
			arguments.addAll(List.of("-ext", extension));
		}
		if (startDate != null) {
			arguments.addAll(List.of("-startdate", startDate));
		}
		keytool(arguments);
	}

	private static void generateSecretKey(final Path store, final String alias) throws Exception {
		keytool(List.of("-genseckey", "-alias", alias, "-keyalg", "AES", "-keysize", "256",
				"-storetype", "pkcs12", "-keystore", store.toString(), "-storepass", PASSWORD));
	}

	private static void exportCertificate(final Path store, final String alias, final Path target) throws Exception {
		keytool(List.of("-exportcert", "-alias", alias, "-keystore", store.toString(),
				"-storepass", PASSWORD, "-file", target.toString()));
	}

	private static void importCertificate(final Path store, final String alias, final Path source) throws Exception {
		keytool(List.of("-importcert", "-noprompt", "-alias", alias, "-file", source.toString(),
				"-storetype", "pkcs12", "-keystore", store.toString(), "-storepass", PASSWORD));
	}

	private static void keytool(final List<String> arguments) throws Exception {
		final var command = new ArrayList<String>();
		command.add(findKeytool());
		command.addAll(arguments);
		final var process = new ProcessBuilder(command).redirectErrorStream(true).start();
		final var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, process.waitFor(), "keytool " + arguments.get(0) + " failed : " + output);
	}

	private static String findKeytool() {
		final var bin = Path.of(System.getProperty("java.home"), "bin");
		for (final var name : List.of("keytool", "keytool.exe")) {
			final var candidate = bin.resolve(name);
			if (Files.isExecutable(candidate)) {
				return candidate.toString();
			}
		}
		throw new IllegalStateException("keytool not found in " + bin + " : a JDK is required to run this test");
	}

	private static void startServer() throws Exception {
		final var keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
		keyManagerFactory.init(loadStore(serverStore), PASSWORD.toCharArray());
		final var sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagerFactory.getKeyManagers(), recordingTrustManagers(loadStore(serverTrustStore)),
				new SecureRandom());

		server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
		server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
			@Override
			public void configure(final HttpsParameters params) {
				final var sslParameters = sslContext.getDefaultSSLParameters();
				sslParameters.setNeedClientAuth(true);
				params.setSSLParameters(sslParameters);
			}
		});
		//answers the principal of the client certificate it received
		server.createContext("/", exchange -> {
			var principal = "none";
			try {
				principal = ((HttpsExchange) exchange).getSSLSession().getPeerPrincipal().toString();
			} catch (final Exception e) {
				//no client certificate : the handshake should not have completed
			}
			final var body = principal.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		serverUri = URI.create("https://localhost:" + server.getAddress().getPort() + "/");
	}

	/**
	 * Server trust managers that record the client certificate they are given before validating it as usual. What
	 * the server actually received is the portable way to assert the client side alias selection : the name of the
	 * TLS alert is not readable from the client on every JDK, and over TLS 1.3 the rejection may not even surface
	 * as a handshake failure.
	 */
	private static TrustManager[] recordingTrustManagers(final KeyStore trustStore) throws Exception {
		final var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);
		final var delegate = (X509ExtendedTrustManager) trustManagerFactory.getTrustManagers()[0];
		return new TrustManager[] { new X509ExtendedTrustManager() {

			private void record(final X509Certificate[] chain) {
				if (chain != null && chain.length > 0) {
					clientPrincipalSeenByServer.set(chain[0].getSubjectX500Principal().toString());
				}
			}

			@Override
			public void checkClientTrusted(final X509Certificate[] chain, final String authType)
					throws CertificateException {
				record(chain);
				delegate.checkClientTrusted(chain, authType);
			}

			@Override
			public void checkClientTrusted(final X509Certificate[] chain, final String authType, final Socket socket)
					throws CertificateException {
				record(chain);
				delegate.checkClientTrusted(chain, authType, socket);
			}

			@Override
			public void checkClientTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine)
					throws CertificateException {
				record(chain);
				delegate.checkClientTrusted(chain, authType, engine);
			}

			@Override
			public void checkServerTrusted(final X509Certificate[] chain, final String authType)
					throws CertificateException {
				delegate.checkServerTrusted(chain, authType);
			}

			@Override
			public void checkServerTrusted(final X509Certificate[] chain, final String authType, final Socket socket)
					throws CertificateException {
				delegate.checkServerTrusted(chain, authType, socket);
			}

			@Override
			public void checkServerTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine)
					throws CertificateException {
				delegate.checkServerTrusted(chain, authType, engine);
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return delegate.getAcceptedIssuers();
			}
		} };
	}

	private static KeyStore loadStore(final Path store) throws Exception {
		final var keyStore = KeyStore.getInstance("pkcs12");
		try (var inputStream = new FileInputStream(store.toFile())) {
			keyStore.load(inputStream, PASSWORD.toCharArray());
		}
		return keyStore;
	}
}
