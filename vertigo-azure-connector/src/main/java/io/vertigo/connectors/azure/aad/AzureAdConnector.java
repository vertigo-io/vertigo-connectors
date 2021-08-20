package io.vertigo.connectors.azure.aad;

import java.net.MalformedURLException;
import java.util.Optional;

import javax.inject.Inject;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 * Component to retrieve a configured ConfidentialClientApplication to connect to AzureAd.
 *
 * @author mlaroche
 *
 */
public class AzureAdConnector implements Connector<ConfidentialClientApplication> {

	private final String connectorName;

	private final String clientId;
	private final String clientSecret;
	private final String authorityUrl;

	@Inject
	public AzureAdConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("clientId") final String clientId,
			@ParamValue("clientSecret") final String clientSecret,
			@ParamValue("authorityUrl") final String authorityUrl) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(clientId)
				.isNotBlank(clientSecret)
				.isNotBlank(authorityUrl);
		//---
		connectorName = connectorNameOpt.orElse("main");

		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.authorityUrl = authorityUrl;

	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * Get a ConfidentialClientApplication
	 * @return the ConfidentialClientApplication
	 */
	@Override
	public ConfidentialClientApplication getClient() {
		try {
			return ConfidentialClientApplication.builder(
					clientId,
					ClientCredentialFactory.createFromSecret(clientSecret))
					.authority(authorityUrl)
					.build();
		} catch (final MalformedURLException e) {
			throw WrappedException.wrap(e);
		}
	}

}
