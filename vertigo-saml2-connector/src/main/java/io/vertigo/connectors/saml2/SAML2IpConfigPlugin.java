package io.vertigo.connectors.saml2;

import java.util.List;

import org.opensaml.security.credential.Credential;

import io.vertigo.core.node.component.Plugin;

public interface SAML2IpConfigPlugin extends Plugin {

	String getLoginUrl();

	List<Credential> getPublicCredentials();

	String getLogoutUrl();
}
