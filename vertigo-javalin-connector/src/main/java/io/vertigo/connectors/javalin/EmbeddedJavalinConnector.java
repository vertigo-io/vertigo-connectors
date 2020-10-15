/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2020, Vertigo.io, team@vertigo.io
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

import java.util.Optional;

import javax.inject.Inject;

import io.javalin.Javalin;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.param.ParamValue;

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
	 */
	@Inject
	public EmbeddedJavalinConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("port") final int javalinPort) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotNull(javalinPort);
		//-----
		connectorName = connectorNameOpt.orElse("main");
		final String tempDir = System.getProperty("java.io.tmpdir");
		javalinApp = Javalin.create(config -> config.ignoreTrailingSlashes = true) //wait for javalin PR#1088 fix
				.before(new JettyMultipartConfig(tempDir))
				.after(new JettyMultipartCleaner());
		port = javalinPort;
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
