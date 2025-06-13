/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2025, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.twitter;

import javax.inject.Inject;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import twitter4j.Twitter;

/**
 * Component to retrieve a configured twitter4j client.
 *
 * @author mlaroche
 */
public class Twitter4jConnector implements Connector<Twitter> {
	private final Twitter twitter;

	@Inject
	public Twitter4jConnector(
			@ParamValue("oauthConsumerKey") final String oauthConsumerKey,
			@ParamValue("oauthConsumerSecret") final String oauthConsumerSecret,
			@ParamValue("oauthAccessToken") final String oauthAccessToken,
			@ParamValue("oauthAccessTokenSecret") final String oauthAccessTokenSecret) {
		Assertion.check()
				.isNotBlank(oauthConsumerKey)
				.isNotBlank(oauthConsumerSecret)
				.isNotBlank(oauthAccessToken)
				.isNotBlank(oauthAccessTokenSecret);
		//---
		// this is the basic conf from vertigo
		twitter = Twitter.newBuilder()
				.oAuthAccessToken(oauthAccessToken, oauthAccessTokenSecret)
				.oAuthConsumer(oauthConsumerKey, oauthConsumerSecret).build();
	}

	/**
	 * @return the twitter client
	 */
	@Override
	public Twitter getClient() {
		return twitter;
	}

}
