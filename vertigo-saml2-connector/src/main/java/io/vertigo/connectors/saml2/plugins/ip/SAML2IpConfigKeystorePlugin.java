package io.vertigo.connectors.saml2.plugins.ip;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.opensaml.security.credential.Credential;

import io.vertigo.connectors.saml2.SAML2IpConfigPlugin;
import io.vertigo.connectors.saml2.plugins.CertUtil;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

public class SAML2IpConfigKeystorePlugin implements SAML2IpConfigPlugin {

	private final String loginUrl;
	private final String logoutUrl;
	private final List<Credential> credentials;

	@Inject
	public SAML2IpConfigKeystorePlugin(
			@ParamValue("loginUrl") final String loginUrl,
			@ParamValue("logoutUrl") final String logoutUrl,
			@ParamValue("keystoreFile") final String keystoreFile,
			@ParamValue("keystorePassword") final String keystorePassword,
			@ParamValue("aliases") final String aliases,
			@ParamValue("keystoreType") final Optional<String> keystoreTypeOpt,
			final ResourceManager resourceManager) {

		this.loginUrl = loginUrl;
		this.logoutUrl = logoutUrl;

		credentials = CertUtil.getCredentialsFromKeystore(
				resourceManager.resolve(keystoreFile),
				aliases.split(";"),
				keystorePassword.toCharArray(),
				false,
				keystoreTypeOpt.orElse("pkcs12"));
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
