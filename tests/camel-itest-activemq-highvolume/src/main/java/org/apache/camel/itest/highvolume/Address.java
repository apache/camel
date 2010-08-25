package org.apache.camel.itest.highvolume;

import java.io.Serializable;

public class Address implements Serializable{
	
	private static final long serialVersionUID = 6058616766070098476L;
	
	private String Street;
	private String Locality;
	private String Number;
	private String PostalCode;
	
	public String getStreet() {
		return Street;
	}
	public void setStreet(String street) {
		Street = street;
	}
	public String getLocality() {
		return Locality;
	}
	public void setLocality(String locality) {
		Locality = locality;
	}
	public String getNumber() {
		return Number;
	}
	public void setNumber(String number) {
		Number = number;
	}
	public String getPostalCode() {
		return PostalCode;
	}
	public void setPostalCode(String postalCode) {
		PostalCode = postalCode;
	}

}
