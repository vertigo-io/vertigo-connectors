package io.vertigo.connectors.saml2.plugins.ip;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opensaml.security.credential.BasicCredential;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;

import io.vertigo.connectors.saml2.SAML2IpConfigPlugin;
import io.vertigo.connectors.saml2.plugins.CertUtil;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.util.StringUtil;

public class SAML2IpConfigStringPlugin implements SAML2IpConfigPlugin {

	private final String loginUrl;
	private final String logoutUrl;
	private final List<Credential> credentials;

	@Inject
	public SAML2IpConfigStringPlugin(
			@ParamValue("loginUrl") final String loginUrl,
			@ParamValue("logoutUrl") final String logoutUrl,
			@ParamValue("publicKey") final String publicKey,
			@ParamValue("publicKey2") final Optional<String> publicKey2Opt) {

		Security.addProvider(new BouncyCastleProvider()); //PKCS1 support

		credentials = new ArrayList<>();

		this.loginUrl = loginUrl;
		this.logoutUrl = logoutUrl;

		credentials.add(getCredentialFromString(publicKey));
		if (publicKey2Opt.isPresent() && !StringUtil.isBlank(publicKey2Opt.get())) {
			credentials.add(getCredentialFromString(publicKey2Opt.get()));
		}

	}

	private static Credential getCredentialFromString(final String publicKeyString) {
		if (publicKeyString.startsWith("-----BEGIN CERTIFICATE-----")) {
			final var pubKey = CertUtil.readX509FromString(publicKeyString);
			return new BasicX509Credential(pubKey);
		}

		final var pubKey = CertUtil.publicKeyFromPem(publicKeyString);
		return new BasicCredential(pubKey);
	}

	@Override
	public String getLoginUrl() {
		return loginUrl;
	}

	@Override
	public String getLogoutUrl() {
		return logoutUrl;
	}

	@Override
	public List<Credential> getPublicCredentials() {
		return credentials;
	}

}
