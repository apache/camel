package org.apache.camel.itest.highvolume;

import java.io.Serializable;

public class Contact implements Serializable{
	
	private static final long serialVersionUID = -8890589951901473064L;

	private String FirstName;
	private String LastName;
	private String Title;
	private String Company;
	
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
	public String getTitle() {
		return Title;
	}
	public void setTitle(String title) {
		Title = title;
	}
	public String getCompany() {
		return Company;
	}
	public void setCompany(String company) {
		Company = company;
	}

}
