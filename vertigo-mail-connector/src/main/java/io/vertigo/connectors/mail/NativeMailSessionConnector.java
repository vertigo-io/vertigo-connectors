/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2024, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.mail;

import java.util.Optional;
import java.util.Properties;

import javax.inject.Inject;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.util.StringUtil;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

/**
 * Plugin de gestion des mails, pour l'implémentation du jdk.
 *
 * @author npiedeloup
 */
public class NativeMailSessionConnector implements MailSessionConnector {

	private final String connectorName;
	private final String mailStoreProtocol;
	private final String mailHost;
	private final Optional<Integer> mailPort;
	private final Optional<String> mailLogin;
	private final Optional<String> mailPassword;

	/**
	 * Crée le plugin d'accès au serveur mail.
	 *
	 * @param mailStoreProtocol Protocole utilisé
	 * @param mailHost Serveur de mail
	 * @param mailPortOpt port à utiliser (facultatif)
	 * @param mailLoginOpt Login à utiliser lors de la connexion au serveur mail (facultatif)
	 * @param mailPasswordOpt mot de passe à utiliser lors de la connexion au serveur mail (facultatif)
	 */
	@Inject
	public NativeMailSessionConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("storeProtocol") final String mailStoreProtocol,
			@ParamValue("host") final String mailHost,
			@ParamValue("port") final Optional<Integer> mailPortOpt,
			@ParamValue("login") final Optional<String> mailLoginOpt,
			@ParamValue("pwd") final Optional<String> mailPasswordOpt) {
		Assertion.check()
				.isNotBlank(mailStoreProtocol)
				.isNotBlank(mailHost)
				.isNotNull(connectorNameOpt)
				.isNotNull(mailPortOpt)
				.isNotNull(mailLoginOpt)
				.isNotNull(mailPasswordOpt)
				.when(mailLoginOpt.isPresent(), () -> Assertion.check()
						.isTrue(!StringUtil.isBlank(mailLoginOpt.get()), // if set, login can't be empty
								"When defined Login can't be empty"))
				.isTrue(mailLoginOpt.isEmpty() ^ mailPasswordOpt.isPresent(), // login and password must be null or not null both
						"Password is required when login is defined");
		//-----
		connectorName = connectorNameOpt.orElse(DEFAULT_CONNECTOR_NAME);
		//-----
		this.mailStoreProtocol = mailStoreProtocol;
		this.mailHost = mailHost;
		mailPort = mailPortOpt;
		mailLogin = mailLoginOpt;
		mailPassword = mailPasswordOpt;
	}

	@Override
	public String getName() {
		return connectorName;
	}

	@Override
	public Session getClient() {
		final Properties properties = new Properties();
		properties.setProperty("mail.store.protocol", mailStoreProtocol);
		properties.setProperty("mail.host", mailHost);
		if (mailPort.isPresent()) {
			properties.setProperty("mail.smtp.port", mailPort.get().toString());
		}
		properties.setProperty("mail.debug", "false");
		final Session session;
		if (mailLogin.isPresent()) {
			properties.setProperty("mail.smtp.ssl.trust", mailHost);
			properties.setProperty("mail.smtp.starttls.enable", "true");
			properties.setProperty("mail.smtp.auth", "true");

			final String username = mailLogin.get();
			final String password = mailPassword.get();
			session = Session.getInstance(properties, new Authenticator() {

				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, password);
				}
			});
		} else {
			session = Session.getDefaultInstance(properties);
		}
		session.setDebug(false);
		return session;
	}
}
