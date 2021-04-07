/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2021, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.httpclient;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.time.Duration;
import java.util.Optional;

import javax.inject.Inject;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 * @author npiedeloup
 */
public class HttpClientConnector implements Connector<HttpClient> {

	private final String connectionName;
	private final Optional<ProxySelector> proxyOpt;
	private final String urlPrefix;
	private final int connectTimeout;

	@Inject
	public HttpClientConnector(
			@ParamValue("name") final Optional<String> connectionNameOpt,
			@ParamValue("urlPrefix") final String urlPrefix,
			@ParamValue("connectTimeoutSecond") final Optional<Integer> connectTimeoutOpt,
			@ParamValue("proxy") final Optional<String> proxyHostOpt,
			@ParamValue("proxyPort") final Optional<Integer> proxyPortOpt) {
		Assertion.check()
				.isNotBlank(urlPrefix)
				.isTrue(urlPrefix.startsWith("http"), "urlPrefix ({0}) must include protocol http or https", urlPrefix)
				.isFalse(urlPrefix.endsWith("/"), "urlPrefix ({0}) mustn't end with /", urlPrefix)
				.when(proxyHostOpt.isPresent(),
						() -> Assertion.check().isTrue(proxyPortOpt.isPresent(), "ProxyPort is mandatory if proxy was set"));
		//---
		connectionName = connectionNameOpt.orElse("main");
		this.urlPrefix = urlPrefix;
		connectTimeout = connectTimeoutOpt.orElse(20);
		proxyOpt = proxyHostOpt.map(proxy -> ProxySelector.of(new InetSocketAddress(proxy, proxyPortOpt.get())));
	}

	@Override
	public HttpClient getClient() {
		final Builder builder = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.followRedirects(Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(connectTimeout));

		HttpClientCookie.getCurrentCookieManager()
				.ifPresent((cookieManager) -> builder.cookieHandler(cookieManager));

		proxyOpt.ifPresent((proxy) -> builder.proxy(proxy));
		return builder.build();
	}

	public String getUrlPrefix() {
		return urlPrefix;
	}

	@Override
	public String getName() {
		return connectionName;
	}

}
