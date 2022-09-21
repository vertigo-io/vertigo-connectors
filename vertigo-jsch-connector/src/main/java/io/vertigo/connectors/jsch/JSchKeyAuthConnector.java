package io.vertigo.connectors.jsch;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Optional;

import javax.inject.Inject;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import com.jcraft.jsch.JSch;

import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

public class JSchKeyAuthConnector implements JSchConnector {

	private JSch jSch;

	@Inject
	public JSchKeyAuthConnector(
			final ResourceManager resourceManager,
			@ParamValue("username") final String username,
			@ParamValue("keyStoreUrl") final String keyStoreUrl,
			@ParamValue("keyStorePassword") final String keyStorePassword,
			@ParamValue("privateKeyAlias") final String privateKeyAlias,
			@ParamValue("knownHostUrlOpt") final Optional<String> knownHostUrlOpt) {
		try {
			final var jSch = new JSch();
			final var keyStoreResolvedUrl = resourceManager.resolve(keyStoreUrl);
			final var jks = KeyStore.getInstance("PKCS12");
			jks.load(keyStoreResolvedUrl.openStream(), keyStorePassword.toCharArray());
			final var privateKey = (PrivateKey) jks.getKey(privateKeyAlias, keyStorePassword.toCharArray());
			final var stringWriter = new StringWriter();
			final var pemWriter = new JcaPEMWriter(stringWriter);
			pemWriter.writeObject(privateKey);
			pemWriter.close();
			final var privateKeyPEM = stringWriter.toString().getBytes(StandardCharsets.UTF_8);

			jSch.addIdentity(username, privateKeyPEM, null, null);

			if (knownHostUrlOpt.isPresent()) {
				jSch.setKnownHosts(knownHostUrlOpt.get());
			}
			this.jSch = jSch;
		} catch (final Exception e) {
			throw WrappedException.wrap(e);
		}

	}

	@Override
	public JSch getClient() {
		return jSch;
	}

}
