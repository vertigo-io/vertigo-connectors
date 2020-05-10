package io.vertigo.connectors.twitter;

import java.util.Properties;

import javax.inject.Inject;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.PropertyConfiguration;

/**
 * Component to retrieve a configured twitter4j client.
 *
 * @author mlaroche
 *
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
				.argNotEmpty(oauthConsumerKey)
				.argNotEmpty(oauthConsumerSecret)
				.argNotEmpty(oauthAccessToken)
				.argNotEmpty(oauthAccessTokenSecret);
		//---
		final Properties propertiesFromConstructor = new Properties();
		propertiesFromConstructor.put("oauth.consumerKey", oauthConsumerKey);
		propertiesFromConstructor.put("oauth.consumerSecret", oauthConsumerSecret);
		propertiesFromConstructor.put("oauth.accessToken", oauthAccessToken);
		propertiesFromConstructor.put("oauth.accessTokenSecret", oauthAccessTokenSecret);
		// this is the basic conf from vertigo
		final Configuration configuration = new PropertyConfiguration(propertiesFromConstructor, "/");// get default config from twitter4j.properties file is classpath
		twitter = new TwitterFactory(configuration).getInstance();
	}

	/**
	 * Get an OSClientV3 from the connection pool
	 *
	 * @return
	 */
	@Override
	public Twitter getClient() {
		return twitter;
	}

}
