package io.vertigo.connectors.mongodb;

import javax.inject.Inject;

import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;

import io.vertigo.connectors.mongodb.data.Person;
import io.vertigo.core.node.AutoCloseableNode;
import io.vertigo.core.node.component.di.DIInjector;
import io.vertigo.core.node.config.BootConfig;
import io.vertigo.core.node.config.NodeConfig;
import io.vertigo.core.param.Param;
import io.vertigo.core.plugins.resource.classpath.ClassPathResourceResolverPlugin;

public class MongoClientConnectorTest {

	@Inject
	private MongoClientConnector mongoClientConnector;
	private AutoCloseableNode node;

	@BeforeEach
	public final void setUp() throws Exception {
		node = new AutoCloseableNode(buildNodeConfig());
		DIInjector.injectMembers(this, node.getComponentSpace());
	}

	@AfterEach
	public final void tearDown() throws Exception {
		if (node != null) {
			node.close();
		}
	}

	@Test
	public void testConnection() {
		final CodecRegistry pojoCodecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
				CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()));

		final MongoDatabase mongoDatabase = mongoClientConnector.getClient().getDatabase("test-connector").withCodecRegistry(pojoCodecRegistry);
		final MongoCollection<Person> personCollection = mongoDatabase.getCollection("person", Person.class);

		final Person randomGuy = new Person();
		randomGuy.setFirstName("My");
		randomGuy.setLastName("Name");

		final InsertOneResult insertResult = personCollection.insertOne(randomGuy);
		final BsonValue randomGuyId = insertResult.getInsertedId();
		final Person queriedPerson = personCollection.find(Filters.eq("firstName", "My")).first();
		Assertions.assertEquals("Name", queriedPerson.getLastName());

	}

	protected NodeConfig buildNodeConfig() {
		return NodeConfig.builder()
				.withBoot(BootConfig.builder()
						.addPlugin(ClassPathResourceResolverPlugin.class)
						.build())
				.addModule(new MongodbFeatures()
						.withMongoClient(
								Param.of("connectionString", "mongodb://docker-vertigo.part.klee.lan.net:27017"))
						.build())
				.build();
	}

}
