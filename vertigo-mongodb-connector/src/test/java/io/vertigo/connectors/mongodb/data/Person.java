package io.vertigo.connectors.mongodb.data;

import org.bson.types.ObjectId;

public class Person {

	private ObjectId id;
	private String firstName;
	private String lastName;

	public ObjectId getId() {
		return id;
	}

	public void setId(final ObjectId id) {
		this.id = id;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(final String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(final String lastName) {
		this.lastName = lastName;
	}

}
