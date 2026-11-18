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
package io.vertigo.connectors.saml2;

import static io.vertigo.connectors.saml2.SAML2TestSupport.IDP_LOGIN_URL;
import static io.vertigo.connectors.saml2.SAML2TestSupport.IDP_LOGOUT_URL;
import static io.vertigo.connectors.saml2.SAML2TestSupport.RESOURCE_MANAGER;
import static io.vertigo.connectors.saml2.SAML2TestSupport.buildIdpResponse;
import static io.vertigo.connectors.saml2.SAML2TestSupport.certificatePem;
import static io.vertigo.connectors.saml2.SAML2TestSupport.generateKeyMaterial;
import static io.vertigo.connectors.saml2.SAML2TestSupport.pkcs1PrivateKeyPem;
import static io.vertigo.connectors.saml2.SAML2TestSupport.pkcs8PrivateKeyPem;
import static io.vertigo.connectors.saml2.SAML2TestSupport.publicKeyPem;
import static io.vertigo.connectors.saml2.SAML2TestSupport.writeFile;
import static io.vertigo.connectors.saml2.SAML2TestSupport.writePkcs12Keystore;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Security;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.crypto.XMLSigningUtil;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;

import io.vertigo.connectors.saml2.SAML2TestSupport.KeyMaterial;
import io.vertigo.connectors.saml2.plugins.ip.SAML2IpConfigFilePlugin;
import io.vertigo.connectors.saml2.plugins.ip.SAML2IpConfigStringPlugin;
import io.vertigo.connectors.saml2.plugins.sp.SAML2SpKeyKeystorePlugin;
import io.vertigo.connectors.saml2.plugins.sp.SAML2SpKeyStringPlugin;

/**
 * The SAML2 connector with keys and certificates generated at runtime, no IdP and no network.
 * The callback steps of vertigo-vega SAML2WebAuthenticationPlugin are replayed (see handleCallback) on a SAMLResponse
 * built like an IdP does : assertion encrypted for the SP, response signed by the IdP.
 * Covers the OpenSAML / Santuario / BouncyCastle crypto on the credentials given by the connector plugins.
 */
class SAML2ConnectorTest {
	private static final String SP_ALIAS = "spkey";
	private static final String KEYSTORE_PASSWORD = "changeit";
	private static final Map<String, List<String>> ATTRIBUTES = Map.of(
			"mail", List.of("user-1@test.fr"),
			"groups", List.of("agent", "admin"));

	private static KeyMaterial idp;
	private static KeyMaterial idpNext;
	private static KeyMaterial sp;

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void setUp() throws Exception {
		OpenSAMLUtil.initOpenSamlIfNeeded();
		idp = generateKeyMaterial("idp");
		idpNext = generateKeyMaterial("idp-next");
		sp = generateKeyMaterial("sp");
	}

	//-----------------------------------------------------------------------------------------------------------------
	// Configuration of rdvPref : saml.ip.file (IdP certificate file) + saml.certif.keystore (PKCS12)
	//-----------------------------------------------------------------------------------------------------------------

	@Test
	void testIpFileAndKeystoreRsaSha256() throws Exception {
		assertRoundTrip(ipFilePlugin(Optional.empty()).getPublicCredentials(), keystorePlugin().getCredentials(), idp, SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
	}

	@Test
	void testIpFileAndKeystoreRsaSha1() throws Exception {
		assertRoundTrip(ipFilePlugin(Optional.empty()).getPublicCredentials(), keystorePlugin().getCredentials(), idp, SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1);
	}

	@Test
	void testIpFileUrls() throws Exception {
		final var ipPlugin = ipFilePlugin(Optional.empty());
		assertEquals(IDP_LOGIN_URL, ipPlugin.getLoginUrl());
		assertEquals(IDP_LOGOUT_URL, ipPlugin.getLogoutUrl());
		assertEquals(1, ipPlugin.getPublicCredentials().size());
	}

	@Test
	void testIpFileNextCertificate() throws Exception {
		//IdP certificate renewal : the response signed with the next certificate is checked with the second credential
		final var nextCertificateFile = writeFile(tempDir, "idp-next.crt", certificatePem(idpNext));
		final var ipPlugin = ipFilePlugin(Optional.of(nextCertificateFile));
		assertEquals(2, ipPlugin.getPublicCredentials().size());
		assertRoundTrip(ipPlugin.getPublicCredentials(), keystorePlugin().getCredentials(), idpNext, SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
	}

	@Test
	void testIpFileEmptyNextCertificate() throws Exception {
		//rdvPref sets nextPublicKeyFile to an empty value when there is no renewal
		assertEquals(1, ipFilePlugin(Optional.of("")).getPublicCredentials().size());
	}

	@Test
	void testSignatureOfAnotherIdpRejected() throws Exception {
		final var samlResponse = buildIdpResponse(idpNext.privateCredential(), sp.publicCredential(), SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256, ATTRIBUTES);
		final var ipCredentials = ipFilePlugin(Optional.empty()).getPublicCredentials();
		final var spCredentials = keystorePlugin().getCredentials();
		assertThrows(SignatureException.class, () -> handleCallback(samlResponse, ipCredentials, spCredentials));
	}

	@Test
	void testSpRequestSignature() throws Exception {
		//the AuthnRequest query string is signed with the SP credential (signatureType of rdvPref : RSA SHA256 / RSA SHA1)
		//and checked by the IdP with the SP certificate given in the SP metadata
		final var spCredential = keystorePlugin().getCredentials().get(0);
		final var signedContent = "SAMLRequest=abc&RelayState=xyz&SigAlg=alg".getBytes(StandardCharsets.UTF_8);
		for (final String signatureType : List.of("RSA SHA256", "RSA SHA1", "rsa-sha512")) {
			final var algorithm = OpenSAMLUtil.resolveSignatureType(signatureType);
			final var signature = XMLSigningUtil.signWithURI(spCredential, algorithm, signedContent);
			assertTrue(XMLSigningUtil.verifyWithURI(sp.publicCredential(), algorithm, signature, signedContent), signatureType);
			assertFalse(XMLSigningUtil.verifyWithURI(idp.publicCredential(), algorithm, signature, signedContent), signatureType);
		}
	}

	//-----------------------------------------------------------------------------------------------------------------
	// Key formats of the String plugins (and File plugins, which read the file then call them)
	//-----------------------------------------------------------------------------------------------------------------

	@Test
	void testSpStringPkcs8AndCertificate() throws Exception {
		final var spPlugin = new SAML2SpKeyStringPlugin(certificatePem(sp), pkcs8PrivateKeyPem(sp), Optional.empty(), Optional.empty());
		assertRoundTrip(ipFilePlugin(Optional.empty()).getPublicCredentials(), spPlugin.getCredentials(), idp, SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
	}

	@Test
	void testSpStringPkcs1AndCertificate() throws Exception {
		//PKCS#1 (BEGIN RSA PRIVATE KEY) is refused by the JDK RSA KeyFactory : read by BouncyCastle, registered by the plugin
		final var spPlugin = new SAML2SpKeyStringPlugin(certificatePem(sp), pkcs1PrivateKeyPem(sp), Optional.empty(), Optional.empty());
		assertNotNull(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME));
		assertRoundTrip(ipFilePlugin(Optional.empty()).getPublicCredentials(), spPlugin.getCredentials(), idp, SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
	}

	@Test
	void testSpStringPkcs1AndPublicKey() throws Exception {
		final var spPlugin = new SAML2SpKeyStringPlugin(publicKeyPem(sp), pkcs1PrivateKeyPem(sp), Optional.empty(), Optional.empty());
		assertRoundTrip(ipFilePlugin(Optional.empty()).getPublicCredentials(), spPlugin.getCredentials(), idp, SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
	}

	@Test
	void testIpStringPublicKey() throws Exception {
		final var ipPlugin = new SAML2IpConfigStringPlugin(IDP_LOGIN_URL, IDP_LOGOUT_URL, publicKeyPem(idp), Optional.empty());
		assertRoundTrip(ipPlugin.getPublicCredentials(), keystorePlugin().getCredentials(), idp, SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
	}

	//-----------------------------------------------------------------------------------------------------------------
	// Params of the connector
	//-----------------------------------------------------------------------------------------------------------------

	@Test
	void testParamsBounds() throws Exception {
		assertDoesNotThrow(() -> connector(Optional.empty(), Optional.empty()));
		assertDoesNotThrow(() -> connector(Optional.of("https://app.test/context"), Optional.of(0)));
		//an empty externalUrl (an empty environment variable) is no externalUrl ; the scheme is case insensitive
		assertDoesNotThrow(() -> connector(Optional.of(""), Optional.empty()));
		assertDoesNotThrow(() -> connector(Optional.of("HTTPS://app.test/context"), Optional.empty()));
		//clockSkewSeconds : from 0 to 600s (beyond, the validity times of the assertion are no longer checked)
		assertThrows(IllegalStateException.class, () -> connector(Optional.empty(), Optional.of(-1)));
		assertThrows(IllegalStateException.class, () -> connector(Optional.empty(), Optional.of(601)));
		//externalUrl : the absolute url of the application, the callback path is added to it
		assertThrows(IllegalStateException.class, () -> connector(Optional.of("app.test/context"), Optional.empty()));
		assertThrows(IllegalStateException.class, () -> connector(Optional.of("https://app.test/context/"), Optional.empty()));
	}

	private static SAML2DeploymentConnector connector(final Optional<String> externalUrl, final Optional<Integer> clockSkewSeconds) throws Exception {
		return new SAML2DeploymentConnector(Optional.empty(), "https://app.test/saml", externalUrl, Optional.empty(), Optional.empty(), Optional.empty(),
				clockSkewSeconds, Optional.empty(), Optional.empty(), Optional.empty(), keystorePlugin(), ipFilePlugin(Optional.empty()), RESOURCE_MANAGER);
	}

	//-----------------------------------------------------------------------------------------------------------------

	private static SAML2IpConfigFilePlugin ipFilePlugin(final Optional<String> nextPublicKeyFile) throws Exception {
		final var certificateFile = writeFile(tempDir, "idp.crt", certificatePem(idp));
		return new SAML2IpConfigFilePlugin(IDP_LOGIN_URL, IDP_LOGOUT_URL, certificateFile, nextPublicKeyFile, RESOURCE_MANAGER);
	}

	private static SAML2SpKeyKeystorePlugin keystorePlugin() throws Exception {
		final var keystoreFile = writePkcs12Keystore(tempDir, "keystore.p12", SP_ALIAS, KEYSTORE_PASSWORD, sp);
		return new SAML2SpKeyKeystorePlugin(keystoreFile, KEYSTORE_PASSWORD, SP_ALIAS, Optional.empty(), RESOURCE_MANAGER);
	}

	private static void assertRoundTrip(final List<Credential> ipCredentials, final List<Credential> spCredentials, final KeyMaterial signingIdp, final String signatureAlgorithm) throws Exception {
		final var samlResponse = buildIdpResponse(signingIdp.privateCredential(), sp.publicCredential(), signatureAlgorithm, ATTRIBUTES);
		final var claims = handleCallback(samlResponse, ipCredentials, spCredentials);
		assertEquals(Map.of("mail", "user-1@test.fr", "groups", List.of("agent", "admin")), claims);
	}

	/**
	 * Same steps as SAML2WebAuthenticationPlugin.doHandleCallback (vertigo-vega) : read the response, check its
	 * signature with the IdP credentials (the first that validates it), decrypt the assertion with the SP credentials.
	 */
	static Map<String, Object> handleCallback(final String samlResponse, final List<Credential> ipCredentials, final List<Credential> spCredentials) throws Exception {
		final var response = OpenSAMLUtil.extractSamlResponse(new ByteArrayInputStream(Base64.getDecoder().decode(samlResponse)));

		SignatureException lastFailure = null;
		var signatureValid = false;
		for (final Credential ipCredential : ipCredentials) {
			try {
				SignatureValidator.validate(response.getSignature(), ipCredential);
				signatureValid = true;
				break;
			} catch (final SignatureException e) {
				lastFailure = e;
			}
		}
		if (!signatureValid) {
			throw lastFailure;
		}

		final var decrypter = new Decrypter(null, new StaticKeyInfoCredentialResolver(spCredentials), new InlineEncryptedKeyResolver());
		decrypter.setRootInNewDocument(true);
		final var assertion = decrypter.decrypt(response.getEncryptedAssertions().get(0));
		return OpenSAMLUtil.extractAttributes(assertion);
	}
}
