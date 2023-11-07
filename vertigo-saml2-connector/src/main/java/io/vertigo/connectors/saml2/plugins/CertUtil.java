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
package io.vertigo.connectors.saml2.plugins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opensaml.security.credential.BasicCredential;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;

import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.lang.WrappedException;

public final class CertUtil {
	private static final Pattern HEADERS_REMOVE_PATTERN = Pattern.compile("(-----.*?-----)|(\\r?\\n|\\r)");

	private CertUtil() {
		// util
	}

	public static Credential getCredentialFromString(final String publicKeyString, final String privateKeyString) {
		final var privKey = privateKeyFromPem(privateKeyString);

		if (publicKeyString.startsWith("-----BEGIN CERTIFICATE-----")) {
			final var pubKey = readX509FromString(publicKeyString);
			return new BasicX509Credential(pubKey, privKey);
		}

		final var pubKey = publicKeyFromPem(publicKeyString);
		return new BasicCredential(pubKey, privKey);
	}

	public static Credential getCredentialFromString(final String publicKeyString) {
		if (publicKeyString.startsWith("-----BEGIN CERTIFICATE-----")) {
			final var pubKey = readX509FromString(publicKeyString);
			return new BasicX509Credential(pubKey);
		}

		final var pubKey = publicKeyFromPem(publicKeyString);
		return new BasicCredential(pubKey);
	}

	public static X509Certificate readX509FromString(final String source) {
		try {
			final var factory = CertificateFactory.getInstance("X.509");
			return (X509Certificate) factory.generateCertificate(
					new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
		} catch (final CertificateException e) {
			throw WrappedException.wrap(e);
		}
	}

	private static PublicKey publicKeyFromPem(final String pemString) {
		final var keySpec = getKeySpecFromPemString(pemString, X509EncodedKeySpec::new);
		try {
			return getRsaKeyFactory().generatePublic(keySpec);
		} catch (final InvalidKeySpecException e) {
			throw WrappedException.wrap(e);
		}
	}

	private static PrivateKey privateKeyFromPem(final String pemString) {
		final var keySpec = getKeySpecFromPemString(pemString, PKCS8EncodedKeySpec::new);
		try {
			return getRsaKeyFactory().generatePrivate(keySpec);
		} catch (final InvalidKeySpecException e) {
			throw WrappedException.wrap(e);
		}
	}

	private static KeySpec getKeySpecFromPemString(final String pemString, final Function<byte[], ? extends KeySpec> constructor) {
		final var key = HEADERS_REMOVE_PATTERN.matcher(pemString).replaceAll("");
		final var publicBytes = Base64.getDecoder().decode(key);
		return constructor.apply(publicBytes);
	}

	private static KeyFactory getRsaKeyFactory() {
		try {
			return KeyFactory.getInstance("RSA");
		} catch (final NoSuchAlgorithmException e) {
			throw WrappedException.wrap(e);
		}
	}

	public static List<Credential> getCredentialsFromKeystore(final URL ksUrl, final String[] aliases, final char[] keystorePassword, final boolean withPrivateKey, final String keystoreType) {
		return Arrays.stream(aliases)
				.map(alias -> getCredentialFromKeystore(ksUrl, alias, keystorePassword, withPrivateKey, keystoreType))
				.toList();
	}

	public static Credential getCredentialFromKeystore(final URL ksUrl, final String alias, final char[] keystorePassword, final boolean withPrivateKey, final String keystoreType) {
		// Note: could have used KeyStoreCredentialResolver from opensaml
		try (final var inputStream = ksUrl.openStream()) {
			final var store = KeyStore.getInstance(keystoreType);
			store.load(inputStream, keystorePassword);

			final var cert = store.getCertificate(alias);
			if (cert == null) {
				throw new VSystemException("No key named '{0}' in '{1}'.", alias, ksUrl.toString());
			}

			if (withPrivateKey) {
				final var key = (PrivateKey) store.getKey(alias, keystorePassword);
				return new BasicCredential(cert.getPublicKey(), key);
			}

			return new BasicCredential(cert.getPublicKey());
		} catch (final KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException e) {
			throw WrappedException.wrap(e);
		}
	}
}
