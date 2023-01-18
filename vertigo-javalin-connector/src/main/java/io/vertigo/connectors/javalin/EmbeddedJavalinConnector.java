/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2022, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.javalin;

import java.net.URL;
import java.security.KeyStore;
import java.util.Optional;

import javax.inject.Inject;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import io.javalin.Javalin;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

/**
 * @author npiedeloup
 */
public class EmbeddedJavalinConnector implements JavalinConnector, Activeable {
	private final Javalin javalinApp;
	private final String connectorName;
	private final int port;

	/**
	 * Constructor.
	 * @param connectorNameOpt name of the connector (main by default)
	 * @param javalinPort Jetty server port
	 * @param sslOpt true is ssl needs to be enabled false by default
	 * @param keyStoreUrlOpt keyStore to use when ssl enabled
	 * @param keyStorePasswordOpt keyStore password
	 * @param sslKeyAliasOpt alias of the server to use when ssl enabled
	 */
	@Inject
	public EmbeddedJavalinConnector(
			final ResourceManager resourceManager,
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("port") final int javalinPort,
			@ParamValue("ssl") final Optional<Boolean> sslOpt,
			@ParamValue("keyStoreUrl") final Optional<String> keyStoreUrlOpt,
			@ParamValue("keyStorePassword") final Optional<String> keyStorePasswordOpt,
			@ParamValue("sslKeyAlias") final Optional<String> sslKeyAliasOpt) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotNull(javalinPort);
		//-----
		connectorName = connectorNameOpt.orElse("main");
		final String tempDir = System.getProperty("java.io.tmpdir");
		final var ssl = sslOpt.orElse(false);
		if (ssl) {
			Assertion.check()
					.isTrue(
							keyStoreUrlOpt.isPresent() && keyStorePasswordOpt.isPresent() && sslKeyAliasOpt.isPresent(),
							"When using Javalin with ssl you must provide 'keyStoreUrl', 'keyStorePassword', 'sslKeyAlias' ");
			//---
			javalinApp = Javalin.create(
					config -> {
						config.routing.ignoreTrailingSlashes = false; //javalin PR#1088 fix
						config.jetty.server(() -> {
							final Server server = new Server();
							final HttpConfiguration httpConfig = new HttpConfiguration();
							// Add the SecureRequestCustomizer because we are using TLS.
							httpConfig.addCustomizer(new SecureRequestCustomizer());

							// The ConnectionFactory for HTTP/1.1.
							final HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);
							// The ConnectionFactory for TLS.
							final SslConnectionFactory tls = new SslConnectionFactory(
									getSslContextFactory(resourceManager.resolve(keyStoreUrlOpt.get()), keyStorePasswordOpt.get(), sslKeyAliasOpt.get()),
									http11.getProtocol());
							final ServerConnector sslConnector = new ServerConnector(server, tls, http11);
							sslConnector.setPort(javalinPort);
							server.setConnectors(new Connector[] { sslConnector });
							return server;
						});
					})
					.before(new JettyMultipartConfig(tempDir))
					.after(new JettyMultipartCleaner());
		} else {
			javalinApp = Javalin.create(config -> config.routing.ignoreTrailingSlashes = false) //javalin PR#1088 fix
					.before(new JettyMultipartConfig(tempDir))
					.after(new JettyMultipartCleaner());
		}
		port = javalinPort;
	}

	private static SslContextFactory.Server getSslContextFactory(final URL keyStoreUrl, final String keyStorePassword, final String sslKeyAlias) {
		try {
			final var jks = KeyStore.getInstance("PKCS12");
			jks.load(keyStoreUrl.openStream(), keyStorePassword.toCharArray());
			final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
			sslContextFactory.setKeyStore(jks);
			sslContextFactory.setKeyStoreType("PKCS12");
			sslContextFactory.setCertAlias(sslKeyAlias);
			sslContextFactory.setKeyStorePassword(keyStorePassword);
			sslContextFactory.setTrustStore(jks);
			sslContextFactory.setTrustStoreType("PKCS12");
			sslContextFactory.setTrustStorePassword(keyStorePassword);
			return sslContextFactory;
		} catch (final Exception e) {
			throw WrappedException.wrap(e);
		}
	}

	/**
	 * @return Javalin resource
	 */
	@Override
	public Javalin getClient() {
		return javalinApp;
	}

	@Override
	public String getName() {
		return connectorName;
	}

	/** {@inheritDoc} */
	@Override
	public void start() {
		javalinApp.start(port);
	}

	/** {@inheritDoc} */
	@Override
	public void stop() {
		javalinApp.stop();
	}

}
