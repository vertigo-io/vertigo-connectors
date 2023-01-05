/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2022, Vertigo.io, team@vertigo.io
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDPolicyBuilder;
import org.opensaml.saml.saml2.metadata.Endpoint;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.impl.KeyDescriptorBuilder;
import org.opensaml.saml.saml2.metadata.impl.SingleSignOnServiceBuilder;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.xmlsec.keyinfo.impl.BasicKeyInfoGeneratorFactory;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.xml.sax.SAXException;

import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.lang.WrappedException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.security.impl.RandomIdentifierGenerationStrategy;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import net.shibboleth.utilities.java.support.xml.ParserPool;

public class OpenSAMLHelper {

	private static RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();

	private static ParserPool parserPool;

	private OpenSAMLHelper() {
		// helper
	}

	public static synchronized ParserPool initOpenSamlIfNeeded() {
		if (parserPool != null) {
			return parserPool;
		}
		parserPool = buildParserPool();

		final var registry = new XMLObjectProviderRegistry();
		registry.setParserPool(parserPool);

		ConfigurationService.register(XMLObjectProviderRegistry.class, registry);
		try {
			InitializationService.initialize();
		} catch (final InitializationException e) {
			throw WrappedException.wrap(e);
		}

		return parserPool;
	}

	private static ParserPool buildParserPool() {
		final var parserPool = new BasicParserPool();
		parserPool.setMaxPoolSize(100);
		parserPool.setCoalescing(true);
		parserPool.setIgnoreComments(true);
		parserPool.setIgnoreElementContentWhitespace(true);
		parserPool.setNamespaceAware(true);
		parserPool.setExpandEntityReferences(false);
		parserPool.setXincludeAware(false);

		final Map<String, Boolean> features = new HashMap<>();
		features.put("http://xml.org/sax/features/external-general-entities", Boolean.FALSE);
		features.put("http://xml.org/sax/features/external-parameter-entities", Boolean.FALSE);
		features.put("http://apache.org/xml/features/disallow-doctype-decl", Boolean.TRUE);
		features.put("http://apache.org/xml/features/validation/schema/normalized-value", Boolean.FALSE);
		features.put("http://javax.xml.XMLConstants/feature/secure-processing", Boolean.TRUE);

		parserPool.setBuilderFeatures(features);
		parserPool.setBuilderAttributes(new HashMap<>());

		try {
			parserPool.initialize();
		} catch (final ComponentInitializationException e) {
			throw WrappedException.wrap(e);
		}
		return parserPool;
	}

	public static NameIDPolicy buildNameIdPolicy() {
		final var nameIDPolicy = new NameIDPolicyBuilder().buildObject();
		nameIDPolicy.setAllowCreate(true);
		nameIDPolicy.setFormat(NameIDType.TRANSIENT);

		return nameIDPolicy;
	}

	public static String resolveSignatureType(final String type) {
		final var normalizedType = type.replaceAll("[\\-\\s]", "").toLowerCase();
		switch (normalizedType) {
			case "rsasha1":
				return SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1;
			case "rsasha256":
				return SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256;
			case "rsasha384":
				return SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA384;
			case "rsasha512":
				return SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA512;
			default:
				throw new VSystemException("Unknown signature type '{0}'.", type);
		}
	}

	public static Issuer buildIssuer(final String issuerName) {
		final var issuer = new IssuerBuilder().buildObject();
		issuer.setValue(issuerName);

		return issuer;
	}

	public static String generateSecureRandomId() {
		return secureRandomIdGenerator.generateIdentifier();
	}

	public static Endpoint urlToEndpoint(final String URL) {
		final var endpoint = new SingleSignOnServiceBuilder().buildObject();
		endpoint.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
		endpoint.setLocation(URL);

		return endpoint;
	}

	public static Response extractSamlResponse(final ByteArrayInputStream is) {
		try {
			final var factory = DocumentBuilderFactory.newInstance();
			// for safety, disable function that might fetch external entities
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

			factory.setNamespaceAware(true);
			final var builder = factory.newDocumentBuilder();
			final var document = builder.parse(is);
			final var out = XMLObjectProviderRegistrySupport.getUnmarshallerFactory().getUnmarshaller(document.getDocumentElement());

			final var responseXmlObj = out.unmarshall(document.getDocumentElement());
			return (Response) responseXmlObj;
		} catch (ParserConfigurationException | SAXException | IOException | UnmarshallingException e) {
			throw WrappedException.wrap(e);
		}
	}

	public static Map<String, Object> extractAttributes(final Assertion assertion) {
		return assertion.getAttributeStatements().stream()
				.flatMap(a -> a.getAttributes().stream())
				.collect(HashMap::new, (m, v) -> m.put(v.getName(), resolveAttributeValue(v)), HashMap::putAll);
	}

	private static Object resolveAttributeValue(final Attribute attribute) {
		final var values = attribute.getAttributeValues();
		if (values.isEmpty()) {
			return null;
		}
		if (values.size() == 1) {
			return values.get(0).getDOM().getTextContent();
		}
		return values.stream()
				.map(o -> o.getDOM().getTextContent())
				.collect(Collectors.toList());
	}

	public static void addKeyDescriptor(final SPSSODescriptor spSSODescriptor, final Credential credential, final UsageType usageType, final boolean isExtractPublicKeyFromCertificate) {
		final var keyInfo = getKeyInfo(credential, isExtractPublicKeyFromCertificate);

		final var signKeyDescriptor = new KeyDescriptorBuilder().buildObject();
		signKeyDescriptor.setUse(usageType);
		signKeyDescriptor.setKeyInfo(keyInfo);

		spSSODescriptor.getKeyDescriptors().add(signKeyDescriptor);
	}

	private static KeyInfo getKeyInfo(final Credential credential, final boolean isExtractPublicKeyFromCertificate) {
		final BasicKeyInfoGeneratorFactory keyInfoGeneratorFactory;
		if (!isExtractPublicKeyFromCertificate && credential instanceof X509Credential) {
			keyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
			((X509KeyInfoGeneratorFactory) keyInfoGeneratorFactory).setEmitEntityCertificate(true);
		} else {
			keyInfoGeneratorFactory = new BasicKeyInfoGeneratorFactory();
			keyInfoGeneratorFactory.setEmitPublicKeyValue(true);
		}
		final var keyInfoGenerator = keyInfoGeneratorFactory.newInstance();

		try {
			return keyInfoGenerator.generate(credential);
		} catch (final SecurityException e) {
			throw WrappedException.wrap(e);
		}
	}
}
