package io.vertigo.connectors.keycloak;

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;

import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeploymentBuilder;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;
import io.vertigo.core.util.ClassUtil;

/**
 * Component to retrieve a configured KeycloakDeployment client.
 *
 * @author mlaroche
 *
 */
public class KeycloakDeploymentConnector implements Connector<AdapterDeploymentContext> {

	private final AdapterDeploymentContext adapterDeploymentContext;
	private final String connectorName;

	@Inject
	public KeycloakDeploymentConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("configUrl") final Optional<String> configUrlOpt,
			@ParamValue("configResolverClass") final Optional<String> configResolverClassOpt,
			final ResourceManager resourceManager) {
		Assertion.check()
				.notNull(connectorNameOpt)
				.notNull(configUrlOpt)
				.notNull(configResolverClassOpt);
		//---
		connectorName = connectorNameOpt.orElse("main");

		if (configResolverClassOpt.isPresent()) {
			//multi-tenant
			adapterDeploymentContext = new AdapterDeploymentContext(ClassUtil.newInstance(configResolverClassOpt.get(), KeycloakConfigResolver.class));
		} else {
			// single-tenant
			final String configUrl = configUrlOpt.orElse("/keycloak.json");// in classpath by default
			Assertion.checkArgNotEmpty(configUrl);
			try {
				adapterDeploymentContext = new AdapterDeploymentContext(KeycloakDeploymentBuilder.build(resourceManager.resolve(configUrl).openStream()));
			} catch (final IOException e) {
				throw WrappedException.wrap(e);
			}
		}

	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * Get a AdapterDeploymentContext
	 * @return
	 */
	@Override
	public AdapterDeploymentContext getClient() {
		return adapterDeploymentContext;
	}

}
