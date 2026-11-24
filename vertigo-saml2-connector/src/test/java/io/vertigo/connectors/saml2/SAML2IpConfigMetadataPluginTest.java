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

import static io.vertigo.connectors.saml2.SAML2TestSupport.IDP_ENTITY_ID;
import static io.vertigo.connectors.saml2.SAML2TestSupport.IDP_LOGIN_URL;
import static io.vertigo.connectors.saml2.SAML2TestSupport.IDP_LOGOUT_URL;
import static io.vertigo.connectors.saml2.SAML2TestSupport.RESOURCE_MANAGER;
import static io.vertigo.connectors.saml2.SAML2TestSupport.buildIdpResponse;
import static io.vertigo.connectors.saml2.SAML2TestSupport.certificatePem;
import static io.vertigo.connectors.saml2.SAML2TestSupport.generateKeyMaterial;
import static io.vertigo.connectors.saml2.SAML2TestSupport.idpMetadata;
import static io.vertigo.connectors.saml2.SAML2TestSupport.writeFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.signature.support.SignatureConstants;

import io.vertigo.connectors.saml2.SAML2TestSupport.KeyMaterial;
import io.vertigo.connectors.saml2.plugins.ip.SAML2IpConfigMetadataPlugin;
import io.vertigo.core.lang.VSystemException;

/**
 * saml.ip.metadata : the IdP configuration read from its metadata file (urls and signing certificate).
 */
class SAML2IpConfigMetadataPluginTest {
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

	@Test
	void testUrls() throws Exception {
		final var plugin = new SAML2IpConfigMetadataPlugin(metadataFile("idp.xml", IDP_ENTITY_ID, idp), Optional.empty(), Optional.empty(), Optional.empty(), RESOURCE_MANAGER);
		assertEquals(IDP_LOGIN_URL, plugin.getLoginUrl());
		assertEquals(IDP_LOGOUT_URL, plugin.getLogoutUrl());
	}

	@Test
	void testSigningCertificate() throws Exception {
		final var plugin = new SAML2IpConfigMetadataPlugin(metadataFile("idp.xml", IDP_ENTITY_ID, idp), Optional.empty(), Optional.empty(), Optional.empty(), RESOURCE_MANAGER);
		assertEquals(1, plugin.getPublicCredentials().size());
		assertSignatureChecked(plugin.getPublicCredentials(), idp);
	}

	@Test
	void testSigningCertificateWithoutUse() throws Exception {
		//a KeyDescriptor without use is for signing and encryption
		final var metadataFile = writeFile(tempDir, "idp-no-use.xml", idpMetadata(IDP_ENTITY_ID, idp, ""));
		final var plugin = new SAML2IpConfigMetadataPlugin(metadataFile, Optional.empty(), Optional.empty(), Optional.empty(), RESOURCE_MANAGER);
		assertSignatureChecked(plugin.getPublicCredentials(), idp);
	}

	@Test
	void testTwoSigningCertificates() throws Exception {
		//IdP key rollover : its metadata publishes the current and the next signing certificates, both trusted (as with nextCertFile)
		final var keyDescriptorNext = "<md:KeyDescriptor use=\"signing\"><ds:KeyInfo><ds:X509Data><ds:X509Certificate>"
				+ Base64.getEncoder().encodeToString(idpNext.certificate().getEncoded())
				+ "</ds:X509Certificate></ds:X509Data></ds:KeyInfo></md:KeyDescriptor>";
		final var metadata = idpMetadata(IDP_ENTITY_ID, idp).replaceFirst("</md:KeyDescriptor>", "</md:KeyDescriptor>" + keyDescriptorNext);
		final var plugin = new SAML2IpConfigMetadataPlugin(writeFile(tempDir, "idp-rollover.xml", metadata), Optional.empty(), Optional.empty(), Optional.empty(), RESOURCE_MANAGER);
		assertEquals(2, plugin.getPublicCredentials().size());
		assertSignatureChecked(plugin.getPublicCredentials(), idp);
		assertSignatureChecked(plugin.getPublicCredentials(), idpNext);
	}

	@Test
	void testAggregateMetadataFile() throws Exception {
		//a metadata file of several entities (federation) : its only IdP is used, whatever its position
		final var plugin = new SAML2IpConfigMetadataPlugin(writeFile(tempDir, "federation.xml", entities(spEntity(), entity(IDP_ENTITY_ID, idp))),
				Optional.empty(), Optional.empty(), Optional.empty(), RESOURCE_MANAGER);
		assertEquals(IDP_LOGIN_URL, plugin.getLoginUrl());
		assertSignatureChecked(plugin.getPublicCredentials(), idp);
		//the signing key of another entity of the file (the SP here) is not trusted
		assertEquals(1, plugin.getPublicCredentials().size());
		assertThrows(Exception.class, () -> assertSignatureChecked(plugin.getPublicCredentials(), idpNext));
		//several IdPs : which one to trust is not guessed
		final var twoIdps = writeFile(tempDir, "federation-two-idps.xml", entities(entity(IDP_ENTITY_ID, idp), entity("https://other-idp.test/saml", idpNext)));
		final var exception = assertThrows(VSystemException.class,
				() -> new SAML2IpConfigMetadataPlugin(twoIdps, Optional.empty(), Optional.empty(), Optional.empty(), RESOURCE_MANAGER));
		assertTrue(exception.getMessage().contains("federation-two-idps.xml") && exception.getMessage().contains("one SAML 2.0 IdP")
				&& exception.getMessage().endsWith(": 2 found."), exception.getMessage());
	}

	private static String entities(final String... entities) {
		return "<md:EntitiesDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\">" + String.join("", entities) + "</md:EntitiesDescriptor>";
	}

	private static String entity(final String entityId, final KeyMaterial signing) throws Exception {
		return idpMetadata(entityId, signing).replaceFirst("<\\?xml[^>]*\\?>", "");
	}

	private static String spEntity() throws Exception {
		return "<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" entityID=\"https://app.test/saml\">"
				+ "<md:SPSSODescriptor protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
				+ "<md:KeyDescriptor use=\"signing\"><ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><ds:X509Data><ds:X509Certificate>"
				+ Base64.getEncoder().encodeToString(idpNext.certificate().getEncoded())
				+ "</ds:X509Certificate></ds:X509Data></ds:KeyInfo></md:KeyDescriptor>"
				+ "<md:AssertionConsumerService Binding=\"" + SAMLConstants.SAML2_POST_BINDING_URI + "\" Location=\"https://app.test/saml/callback\" index=\"0\"/>"
				+ "</md:SPSSODescriptor></md:EntityDescriptor>";
	}

	@Test
	void testNoSigningCertificate() throws Exception {
		//fails at startup, not at each login
		final var metadataFile = writeFile(tempDir, "idp-encryption-only.xml", idpMetadata(IDP_ENTITY_ID, idp, " use=\"encryption\""));
		final var exception = assertThrows(VSystemException.class,
				() -> new SAML2IpConfigMetadataPlugin(metadataFile, Optional.empty(), Optional.empty(), Optional.empty(), RESOURCE_MANAGER));
		assertTrue(exception.getMessage().startsWith("No IdP signing certificate"), exception.getMessage());
	}

	@Test
	void testNextMetadataFileSameEntity() throws Exception {
		//IdP certificate renewal : same IdP (entityID), new certificate in the next metadata file
		final var plugin = new SAML2IpConfigMetadataPlugin(metadataFile("idp.xml", IDP_ENTITY_ID, idp),
				Optional.of(metadataFile("idp-next.xml", IDP_ENTITY_ID, idpNext)), Optional.empty(), Optional.empty(), RESOURCE_MANAGER);
		assertEquals(2, plugin.getPublicCredentials().size());
		assertSignatureChecked(plugin.getPublicCredentials(), idpNext);
	}

	@Test
	void testNextMetadataFileOtherEntity() throws Exception {
		//IdP change : another entityID in the next metadata file
		final var plugin = new SAML2IpConfigMetadataPlugin(metadataFile("idp.xml", IDP_ENTITY_ID, idp),
				Optional.of(metadataFile("idp-other.xml", "https://other-idp.test/saml", idpNext)), Optional.empty(), Optional.empty(), RESOURCE_MANAGER);
		assertEquals(2, plugin.getPublicCredentials().size());
		assertSignatureChecked(plugin.getPublicCredentials(), idpNext);
	}

	@Test
	void testNextCertFile() throws Exception {
		//nextCertFile : the next IdP certificate, given as a file like metadataFile and nextMetadataFile
		final var nextCertFile = writeFile(tempDir, "idp-next.crt", certificatePem(idpNext));
		final var plugin = new SAML2IpConfigMetadataPlugin(metadataFile("idp.xml", IDP_ENTITY_ID, idp),
				Optional.empty(), Optional.of(nextCertFile), Optional.empty(), RESOURCE_MANAGER);
		assertEquals(2, plugin.getPublicCredentials().size());
		assertSignatureChecked(plugin.getPublicCredentials(), idpNext);
	}

	private static String metadataFile(final String fileName, final String entityId, final KeyMaterial signing) throws Exception {
		return writeFile(tempDir, fileName, idpMetadata(entityId, signing));
	}

	private static void assertSignatureChecked(final List<Credential> ipCredentials, final KeyMaterial signingIdp) throws Exception {
		final Map<String, List<String>> attributes = Map.of("mail", List.of("user-1@test.fr"));
		final var samlResponse = buildIdpResponse(signingIdp.privateCredential(), sp.publicCredential(), SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256, attributes);
		assertEquals(Map.of("mail", "user-1@test.fr"), SAML2ConnectorTest.handleCallback(samlResponse, ipCredentials, List.of(sp.privateCredential())));
	}
}
