/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2023, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.openstack;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.Optional;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.core.transport.Config;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.openstack.OSFactory;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

/**
 * Component to handle the authentification and the connection to any OpenStack service.
 *
 * @author mlaroche, tingargiola
 */
public class OpenStackConnector implements Connector<OSClientV3> {

	/**
	 * Max connection number for the connection pool
	 */
	private static final int MAX_CONNECTION = 100;

	private final Config myConfig;
	private OSClientV3 osClientV3;

	private final String myAuthenticationUrl;
	private final String myUserDomain;
	private final String myUserName;
	private final String myUserSecret;
	private final String myProjectDomain;
	private final String myProjectName;

	private final ResourceManager resourceManager;

	private final Object clientLock = new Object();

	/**
	 * Inject constructor.
	 *
	 * @param authenticationUrl Url of the identity api (ex:
	 * https://keystone.openstack.klee.lan.net:5000/v3/)
	 * @param userDomain Domain of the user.
	 * @param userName Name of the user
	 * @param userSecret Password of the user
	 * @param projectName Name of the project
	 * @param projectDomain DOmain of the project
	 */
	@Inject
	public OpenStackConnector(
			@ParamValue("authenticationUrl") final String authenticationUrl,
			@ParamValue("userDomain") final String userDomain,
			@ParamValue("userName") final String userName,
			@ParamValue("userSecret") final String userSecret,
			@ParamValue("projectDomain") final String projectDomain,
			@ParamValue("projectName") final String projectName,
			@ParamValue("trustoreFile") final Optional<String> trustoreFileOpt,
			@ParamValue("trustorePswd") final Optional<String> trustorePswdOpt,
			@ParamValue("enableSSL") final Optional<Boolean> enableSSLOpt,
			final ResourceManager resourceManager) {
		Assertion.check()
				.isNotBlank(authenticationUrl)
				.isNotBlank(userDomain)
				.isNotBlank(userName)
				.isNotBlank(userSecret)
				.isNotBlank(projectName)
				.isNotBlank(projectDomain)
				.isNotNull(trustoreFileOpt)
				.isNotNull(trustorePswdOpt)
				.isNotNull(resourceManager);
		//---
		this.resourceManager = resourceManager;
		myAuthenticationUrl = authenticationUrl;
		myUserDomain = userDomain;
		myUserName = userName;
		myUserSecret = userSecret;
		myProjectDomain = projectDomain;
		myProjectName = projectName;
		myConfig = buildConfig(trustoreFileOpt, trustorePswdOpt, enableSSLOpt);
		authenticateClient(
				authenticationUrl,
				userDomain,
				userName,
				userSecret,
				projectDomain,
				projectDomain,
				myConfig);
	}

	/**
	 * Get an OSClientV3 from the connection pool
	 *
	 * @return the OpenStack client
	 */
	@Override
	public OSClientV3 getClient() {
		if (osClientV3.getToken().getExpires().toInstant().isBefore(Instant.now().plusSeconds(2))) {
			synchronized (clientLock) {
				osClientV3 = authenticateClient(
						myAuthenticationUrl,
						myUserDomain,
						myUserName,
						myUserSecret,
						myProjectDomain,
						myProjectName,
						myConfig);
			}
		}
		// authent is kept in a threadLocal in the underlying, so we must create new client each time
		// but we reuse the already validated token if possible
		return OSFactory.clientFromToken(osClientV3.getToken(), myConfig);
	}

	protected OSClientV3 authenticateClient(
			final String authenticationUrl,
			final String domain,
			final String user,
			final String secret,
			final String projectDomain,
			final String projectName,
			final Config config) {
		return OSFactory.builderV3().endpoint(authenticationUrl).withConfig(config)
				.credentials(user, secret, Identifier.byName(domain))
				.scopeToProject(Identifier.byName(projectName), Identifier.byName(projectDomain))
				.authenticate();
	}

	protected Config buildConfig(
			final Optional<String> trustoreFileOpt,
			final Optional<String> trustorePswdOpt,
			final Optional<Boolean> enableSSLOpt) {
		final boolean enableSSL = enableSSLOpt.isPresent() && enableSSLOpt.get();
		final Config config = Config.newConfig().withMaxConnections(MAX_CONNECTION);
		if (enableSSL && trustoreFileOpt.isPresent()) {
			try {
				final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
				try (InputStream trustStoreInputStream = resourceManager.resolve(trustoreFileOpt.get()).openStream()) {
					trustStore.load(trustStoreInputStream, trustorePswdOpt.get().toCharArray());
				}
				final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				trustManagerFactory.init(trustStore);
				final SSLContext sslContext = SSLContext.getInstance("TLS");
				sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
				config.withSSLContext(sslContext);
			} catch (NoSuchAlgorithmException | CertificateException | IOException | KeyStoreException | KeyManagementException e) {
				throw WrappedException.wrap(e);
			}
		}
		if (!enableSSL) {
			config.withSSLVerificationDisabled();
		}

		return config;
	}

}
