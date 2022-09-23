package io.vertigo.connectors.saml2;

import java.util.List;

import org.opensaml.security.credential.Credential;

import io.vertigo.core.node.component.Plugin;

public interface SAML2SpKeyPlugin extends Plugin {

	List<Credential> getCredentials();

	default Credential getCredential() {
		return getCredentials().get(0);
	}
}
