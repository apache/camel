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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.PojoProxyHelper;
import org.junit.Test;

public class PojoProxyHelperRequestReplyTest extends ContextTestSupport {

    PersonReceiver receiver = new PersonReceiver();
    
    @Test
    public void testRequestReply() throws Exception {
        Endpoint personEndpoint = context.getEndpoint("direct:person");
        Person person = new Person("Chris");
        PersonHandler sender = PojoProxyHelper.createProxy(personEndpoint, PersonHandler.class);
        
        Person resultPerson = sender.onPerson(person);
        assertEquals(person.getName() + "1", resultPerson.getName());
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:person").bean(receiver);
            }
        };
    }
    
    public final class PersonReceiver implements PersonHandler {
        @Override
        public Person onPerson(Person person) {
            return new Person(person.getName() + "1");
        }
    }

    public interface PersonHandler {
        Person onPerson(Person person);
    }

}
