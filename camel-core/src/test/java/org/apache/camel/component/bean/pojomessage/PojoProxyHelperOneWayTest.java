/**
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
package org.apache.camel.component.bean.pojomessage;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.PojoProxyHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class PojoProxyHelperOneWayTest extends ContextTestSupport {

    PersonReceiver receiver = new PersonReceiver();
    
    @Test
    public void testOneWay() throws Exception {
        Endpoint personEndpoint = context.getEndpoint("direct:person");
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        Person person = new Person("Chris");
        result.expectedBodiesReceived(person);
        PersonHandler sender = PojoProxyHelper.createProxy(personEndpoint, PersonHandler.class);
        
        sender.onPerson(person);
        
        result.assertIsSatisfied();
        assertEquals(1, receiver.receivedPersons.size());
        assertEquals(person.getName(), receiver.receivedPersons.get(0).getName());
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            
            @Override
            public void configure() throws Exception {
                from("direct:person").to("mock:result").bean(receiver);
            }
        };
    }
    
    public final class PersonReceiver implements PersonHandler {
        public List<Person> receivedPersons = new ArrayList<Person>();

        @Override
        public void onPerson(Person person) {
            receivedPersons.add(person);
        }
    }

    public interface PersonHandler {
        void onPerson(Person person);
    }
}
