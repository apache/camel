/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.converter.jaxb;

import jakarta.xml.bind.JAXBElement;

import javax.xml.namespace.QName;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.person.Person;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JAXBElementFallbackTypeConverterTest extends CamelTestSupport {

    @Test
    void testJaxbFallbackTypeConverter() {
        Person person = new Person();
        person.setFirstName("Apache");
        person.setLastName("Camel");

        QName qName = new QName("person.jaxb.converter.camel.apache.org", "person");
        JAXBElement<Person> personElement = new JAXBElement<>(qName, Person.class, person);

        Person result = template.requestBody("direct:start", personElement, Person.class);
        assertNotNull(result);
        assertEquals(person.getFirstName(), result.getFirstName());
        assertEquals(person.getLastName(), result.getLastName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").process(exchange -> {
                    Message in = exchange.getIn();
                    Person body = in.getMandatoryBody(Person.class);
                    exchange.getMessage().setBody(body);
                });
            }
        };
    }
}
