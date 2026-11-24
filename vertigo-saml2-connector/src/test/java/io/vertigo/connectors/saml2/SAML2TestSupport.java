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

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.common.SignableSAMLObject;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.encryption.Encrypter;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.EncryptionConstants;
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;

import io.vertigo.core.resource.ResourceManager;
import net.shibboleth.shared.xml.SerializeSupport;

/**
 * Test material of the SAML2 connector : RSA keys and self-signed certificates generated at runtime (no key in the
 * repository), their PEM forms, and the SAMLResponse an IdP would post back (signed, with an encrypted assertion).
 */
final class SAML2TestSupport {
	static final String IDP_ENTITY_ID = "https://idp.test/saml";
	static final String IDP_LOGIN_URL = "https://idp.test/saml/singleSignOn";
	static final String IDP_LOGOUT_URL = "https://idp.test/saml/singleLogout";

	/** Resolves a resource from its URL, like the file: URLs of the projects configurations. */
	static final ResourceManager RESOURCE_MANAGER = new ResourceManager() {
		@Override
		public URL resolve(final String resource) {
			try {
				return URI.create(resource).toURL();
			} catch (final IOException e) {
				throw new IllegalStateException(e);
			}
		}
	};

	/** A RSA key pair and its self-signed certificate. */
	record KeyMaterial(KeyPair keyPair, X509Certificate certificate) {

		Credential publicCredential() {
			return new BasicX509Credential(certificate);
		}

		Credential privateCredential() {
			return new BasicX509Credential(certificate, keyPair.getPrivate());
		}
	}

	private SAML2TestSupport() {
		//util
	}

	static KeyMaterial generateKeyMaterial(final String commonName) throws Exception {
		final var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		final var keyPair = generator.generateKeyPair();
		final var name = new X500Name("CN=" + commonName);
		final var now = Instant.now();
		final var certificateHolder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now.toEpochMilli()),
				Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(Duration.ofDays(365))), name, keyPair.getPublic())
				.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate()));
		return new KeyMaterial(keyPair, new JcaX509CertificateConverter().getCertificate(certificateHolder));
	}

	//-----------------------------------------------------------------------------------------------------------------
	// PEM
	//-----------------------------------------------------------------------------------------------------------------

	static String certificatePem(final KeyMaterial keyMaterial) throws Exception {
		return pem("CERTIFICATE", keyMaterial.certificate().getEncoded());
	}

	/** X.509 SubjectPublicKeyInfo : BEGIN PUBLIC KEY. */
	static String publicKeyPem(final KeyMaterial keyMaterial) {
		return pem("PUBLIC KEY", keyMaterial.keyPair().getPublic().getEncoded());
	}

	/** PKCS#8 : BEGIN PRIVATE KEY. */
	static String pkcs8PrivateKeyPem(final KeyMaterial keyMaterial) {
		return pem("PRIVATE KEY", keyMaterial.keyPair().getPrivate().getEncoded());
	}

	/** PKCS#1, the traditional OpenSSL format : BEGIN RSA PRIVATE KEY. */
	static String pkcs1PrivateKeyPem(final KeyMaterial keyMaterial) throws IOException {
		final var pkcs1 = PrivateKeyInfo.getInstance(keyMaterial.keyPair().getPrivate().getEncoded())
				.parsePrivateKey().toASN1Primitive().getEncoded();
		return pem("RSA PRIVATE KEY", pkcs1);
	}

	private static String pem(final String type, final byte[] der) {
		final var base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
		return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
	}

	//-----------------------------------------------------------------------------------------------------------------
	// Files
	//-----------------------------------------------------------------------------------------------------------------

	/** @return the file: URL of the written file, as configured by the projects */
	static String writeFile(final Path dir, final String fileName, final String content) throws IOException {
		final var file = dir.resolve(fileName);
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file.toUri().toString();
	}

	/** @return the file: URL of a PKCS12 keystore holding the private key and certificate under this alias */
	static String writePkcs12Keystore(final Path dir, final String fileName, final String alias, final String password, final KeyMaterial keyMaterial) throws Exception {
		final var keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(null, null);
		keyStore.setKeyEntry(alias, keyMaterial.keyPair().getPrivate(), password.toCharArray(), new Certificate[] { keyMaterial.certificate() });
		final var file = dir.resolve(fileName);
		try (var out = Files.newOutputStream(file)) {
			keyStore.store(out, password.toCharArray());
		}
		return file.toUri().toString();
	}

	/** IdP metadata : one signing certificate, the SSO and logout endpoints in HTTP-POST. */
	static String idpMetadata(final String entityId, final KeyMaterial idpSigning) throws Exception {
		return idpMetadata(entityId, idpSigning, " use=\"signing\"");
	}

	/**
	 * IdP metadata : one certificate, the SSO and logout endpoints in HTTP-POST.
	 * @param keyUseAttribute attribute use of the KeyDescriptor, or "" : without use, the certificate is for signing and encryption
	 */
	static String idpMetadata(final String entityId, final KeyMaterial idpSigning, final String keyUseAttribute) throws Exception {
		final var certificate = Base64.getEncoder().encodeToString(idpSigning.certificate().getEncoded());
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" entityID="%s">
					<md:IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
						<md:KeyDescriptor%s>
							<ds:KeyInfo><ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data></ds:KeyInfo>
						</md:KeyDescriptor>
						<md:SingleLogoutService Binding="%s" Location="%s"/>
						<md:SingleSignOnService Binding="%s" Location="%s"/>
					</md:IDPSSODescriptor>
				</md:EntityDescriptor>
				""".formatted(entityId, keyUseAttribute, certificate,
				SAMLConstants.SAML2_POST_BINDING_URI, IDP_LOGOUT_URL,
				SAMLConstants.SAML2_POST_BINDING_URI, IDP_LOGIN_URL);
	}

	//-----------------------------------------------------------------------------------------------------------------
	// SAMLResponse posted by the IdP
	//-----------------------------------------------------------------------------------------------------------------

	/**
	 * The SAMLResponse an IdP posts back : the assertion is encrypted for the SP and the response is signed by the IdP.
	 * @return the SAMLResponse request parameter (base64 of the response xml)
	 */
	static String buildIdpResponse(final Credential idpSigningCredential, final Credential spEncryptionCredential,
			final String signatureAlgorithm, final Map<String, List<String>> attributes) throws Exception {
		final var builders = XMLObjectProviderRegistrySupport.getBuilderFactory();

		final Assertion assertion = build(builders, Assertion.DEFAULT_ELEMENT_NAME);
		assertion.setID(OpenSAMLUtil.generateSecureRandomId());
		assertion.setVersion(SAMLVersion.VERSION_20);
		assertion.setIssueInstant(Instant.now());
		assertion.setIssuer(OpenSAMLUtil.buildIssuer(IDP_ENTITY_ID));
		final Subject subject = build(builders, Subject.DEFAULT_ELEMENT_NAME);
		final NameID nameId = build(builders, NameID.DEFAULT_ELEMENT_NAME);
		nameId.setValue("user-1");
		subject.setNameID(nameId);
		assertion.setSubject(subject);
		final AttributeStatement attributeStatement = build(builders, AttributeStatement.DEFAULT_ELEMENT_NAME);
		attributes.forEach((name, values) -> {
			final Attribute attribute = build(builders, Attribute.DEFAULT_ELEMENT_NAME);
			attribute.setName(name);
			for (final String value : values) {
				final XSString attributeValue = (XSString) Objects.requireNonNull(builders.getBuilder(XSString.TYPE_NAME))
						.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
				attributeValue.setValue(value);
				attribute.getAttributeValues().add(attributeValue);
			}
			attributeStatement.getAttributes().add(attribute);
		});
		assertion.getAttributeStatements().add(attributeStatement);

		final var dataEncryptionParameters = new DataEncryptionParameters();
		dataEncryptionParameters.setAlgorithm(EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES256_GCM);
		final var keyEncryptionParameters = new KeyEncryptionParameters();
		keyEncryptionParameters.setEncryptionCredential(spEncryptionCredential);
		keyEncryptionParameters.setAlgorithm(EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP);
		final var encrypter = new Encrypter(dataEncryptionParameters, keyEncryptionParameters);
		encrypter.setKeyPlacement(Encrypter.KeyPlacement.INLINE);

		final Response response = build(builders, Response.DEFAULT_ELEMENT_NAME);
		response.setID(OpenSAMLUtil.generateSecureRandomId());
		response.setVersion(SAMLVersion.VERSION_20);
		response.setIssueInstant(Instant.now());
		response.setIssuer(OpenSAMLUtil.buildIssuer(IDP_ENTITY_ID));
		final Status status = build(builders, Status.DEFAULT_ELEMENT_NAME);
		final StatusCode statusCode = build(builders, StatusCode.DEFAULT_ELEMENT_NAME);
		statusCode.setValue(StatusCode.SUCCESS);
		status.setStatusCode(statusCode);
		response.setStatus(status);
		response.getEncryptedAssertions().add(encrypter.encrypt(assertion));

		sign(builders, response, idpSigningCredential, signatureAlgorithm);
		final var xml = SerializeSupport.nodeToString(response.getDOM());
		return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
	}

	private static void sign(final XMLObjectBuilderFactory builders, final SignableSAMLObject signableObject, final Credential signingCredential, final String signatureAlgorithm) throws Exception {
		final Signature signature = build(builders, Signature.DEFAULT_ELEMENT_NAME);
		signature.setSigningCredential(signingCredential);
		signature.setSignatureAlgorithm(signatureAlgorithm);
		signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
		signableObject.setSignature(signature);
		Objects.requireNonNull(XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(signableObject)).marshall(signableObject);
		Signer.signObject(signature);
	}

	@SuppressWarnings("unchecked")
	private static <X extends XMLObject> X build(final XMLObjectBuilderFactory builders, final javax.xml.namespace.QName elementName) {
		return (X) Objects.requireNonNull(builders.getBuilder(elementName), elementName::toString).buildObject(elementName);
	}
}
