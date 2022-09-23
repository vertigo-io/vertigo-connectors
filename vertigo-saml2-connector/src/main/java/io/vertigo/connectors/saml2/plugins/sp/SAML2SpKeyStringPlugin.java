package io.vertigo.connectors.saml2.plugins.sp;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opensaml.security.credential.BasicCredential;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;

import io.vertigo.connectors.saml2.SAML2SpKeyPlugin;
import io.vertigo.connectors.saml2.plugins.CertUtil;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.util.StringUtil;

public class SAML2SpKeyStringPlugin implements SAML2SpKeyPlugin {

	private final List<Credential> credentials;

	@Inject
	public SAML2SpKeyStringPlugin(
			@ParamValue("publicKey") final String myPublicKey,
			@ParamValue("privateKey") final String myPrivateKey,
			@ParamValue("publicKey2") final Optional<String> myPublicKey2,
			@ParamValue("privateKey2") final Optional<String> myPrivateKey2) {

		Security.addProvider(new BouncyCastleProvider()); //PKCS1 support

		credentials = new ArrayList<>();
		credentials.add(getCredentialFromString(myPublicKey, myPrivateKey));

		if (myPublicKey2.isPresent() != myPrivateKey2.isPresent()
				|| (myPublicKey2.isPresent() && StringUtil.isBlank(myPublicKey2.get()) != StringUtil.isBlank(myPrivateKey2.get()))) {
			throw new VSystemException("publicKey2 and privateKey2 must be defined accordingly.");
		}

		if (myPublicKey2.isPresent()) {
			credentials.add(getCredentialFromString(myPublicKey2.get(), myPrivateKey2.get()));
		}
	}

	private static Credential getCredentialFromString(final String publicKeyString, final String privateKeyString) {
		final var privKey = CertUtil.privateKeyFromPem(privateKeyString);

		if (publicKeyString.startsWith("-----BEGIN CERTIFICATE-----")) {
			final var pubKey = CertUtil.readX509FromString(publicKeyString);
			return new BasicX509Credential(pubKey, privKey);
		}

		final var pubKey = CertUtil.publicKeyFromPem(publicKeyString);
		return new BasicCredential(pubKey, privKey);
	}

	@Override
	public List<Credential> getCredentials() {
		return credentials;
	}
}
