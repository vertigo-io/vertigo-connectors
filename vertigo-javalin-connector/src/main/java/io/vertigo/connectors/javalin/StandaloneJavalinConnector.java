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
package io.vertigo.connectors.javalin;

import java.util.Optional;

import javax.inject.Inject;

import io.javalin.Javalin;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.param.ParamValue;

/**
 * @author npiedeloup
 */
public class StandaloneJavalinConnector implements JavalinConnector {
	private final Javalin javalinApp;
	private final String connectorName;

	/**
	 * Constructor.
	 * @param connectorNameOpt name of the connector (main by default)
	 */
	@Inject
	public StandaloneJavalinConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("maxRequestSize") final Optional<Long> maxRequestSizeOpt) {
		Assertion.check().isNotNull(connectorNameOpt);
		//-----
		connectorName = connectorNameOpt.orElse("main");
		javalinApp = Javalin.createStandalone(config -> {
			config.routing.ignoreTrailingSlashes = false; //javalin PR#1088 fix
			config.http.maxRequestSize = maxRequestSizeOpt.orElse(10 * 1024L); //limit request size
		});
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
}
