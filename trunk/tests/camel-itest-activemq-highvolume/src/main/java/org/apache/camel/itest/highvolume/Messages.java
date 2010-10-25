package org.apache.camel.itest.highvolume;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;

public class Messages {
	
	@EndpointInject(ref="Direct")
    ProducerTemplate template;
	
	@EndpointInject(ref="JmsQueueProducer")
    ProducerTemplate templateActiveMq;
	
	public void splitString(Exchange exchange) {
		String messages = (String) exchange.getIn().getBody();
		
		String[] result = messages.split(",");
		for (int x=0; x<result.length; x++) {
			templateActiveMq.sendBody(result[x]);
		}
	}
	
	public void splitObject(Exchange exchange) {
		List<Person> persons = (List) exchange.getIn().getBody();
		
		for (Person person : persons) {
			templateActiveMq.sendBody(person);
		}
	}
    
    public void generateString(Exchange exchange) {
     	StringBuilder messages = new StringBuilder();
        for (int i = 1; i < 20000; i++) {
        	messages.append("Test Message: " + i + ",");
        }
        template.sendBody(messages.toString());
    }
    
    public void generateObject(Exchange exchange) {
    	Person person;
    	List<Person> persons = new ArrayList();
        for (int i = 1; i < 15000; i++) {
        	person = createPerson();
        	person.setId(i);
        	
        	persons.add(person);
        }
        
        template.sendBody(persons);
        
    }
    
    private static Person createPerson() {
    	Person person = new Person();
    	Address address = new Address();
    	Contact contact = new Contact();
    	List<Contact> contacts = new ArrayList<Contact>();
    	
    	address.setLocality("ApacheWorld");
    	address.setNumber("666");
    	address.setPostalCode("B-1000");
    	address.setStreet("CommitterStreet");
    	
    	// First contact
    	contact.setCompany("Fuse");
    	contact.setFirstName("Claus");
    	contact.setLastName("Ibsen");
    	contact.setTitle("Camel Designer");
    	contacts.add(contact);
    	
    	// 2nd contact
    	contact.setCompany("Fuse");
    	contact.setFirstName("Guillaume");
    	contact.setLastName("Nodet");
    	contact.setTitle("Karaf Designer");
    	contacts.add(contact);
    	
    	// 3rd contact
    	contact.setCompany("Apache Foundation");
    	contact.setFirstName("Jean-Baptiste");
    	contact.setLastName("D'Onofré");
    	contact.setTitle("ServiceMix committer");
    	contacts.add(contact);
    	
    	person.setFirstName("Charles");
    	person.setLastName("Moulliard");
    	person.setAddress(address);
    	person.setContacts(contacts);
   	
    	return person;
    }

}
