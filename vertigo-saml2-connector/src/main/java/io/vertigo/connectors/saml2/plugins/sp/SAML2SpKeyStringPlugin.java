package io.vertigo.connectors.saml2.plugins.sp;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opensaml.security.credential.Credential;

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
			@ParamValue("nextPublicKey") final Optional<String> nextPublicKeyOpt,
			@ParamValue("nextPrivateKey") final Optional<String> nextPrivateKeyOpt) {

		Security.addProvider(new BouncyCastleProvider()); //PKCS1 support

		credentials = new ArrayList<>();
		credentials.add(CertUtil.getCredentialFromString(myPublicKey, myPrivateKey));

		if (nextPublicKeyOpt.isPresent() != nextPrivateKeyOpt.isPresent()
				|| (nextPublicKeyOpt.isPresent() && StringUtil.isBlank(nextPublicKeyOpt.get()) != StringUtil.isBlank(nextPrivateKeyOpt.get()))) {
			throw new VSystemException("publicKey2 and privateKey2 must be defined accordingly.");
		}

		if (nextPublicKeyOpt.isPresent()) {
			credentials.add(CertUtil.getCredentialFromString(nextPublicKeyOpt.get(), nextPrivateKeyOpt.get()));
		}
	}

	@Override
	public List<Credential> getCredentials() {
		return credentials;
	}
}
