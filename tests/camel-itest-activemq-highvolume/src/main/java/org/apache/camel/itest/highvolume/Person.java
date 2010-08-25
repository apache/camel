package org.apache.camel.itest.highvolume;

import java.io.Serializable;
import java.util.List;

public class Person implements Serializable {
	
	private static final long serialVersionUID = 1411519840977233192L;

	private String FirstName;
	private String LastName;
	private long Id;
	private int Age;
	private Address address;
	private List<Contact> contacts;
	
	public String getFirstName() {
		return FirstName;
	}
	public void setFirstName(String firstName) {
		FirstName = firstName;
	}
	public String getLastName() {
		return LastName;
	}
	public void setLastName(String lastName) {
		LastName = lastName;
	}
	public int getAge() {
		return Age;
	}
	public void setAge(int age) {
		Age = age;
	}
	public Address getAddress() {
		return address;
	}
	public void setAddress(Address address) {
		this.address = address;
	}
	public List<Contact> getContacts() {
		return contacts;
	}
	public void setContacts(List<Contact> contacts) {
		this.contacts = contacts;
	}
	public long getId() {
		return Id;
	}
	public void setId(long id) {
		Id = id;
	}
	
	

}
