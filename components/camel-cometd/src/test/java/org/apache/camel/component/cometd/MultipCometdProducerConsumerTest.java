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
package org.apache.camel.component.cometd;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit testing for using a CometdProducer and a CometdConsumer
 */
public class MultipCometdProducerConsumerTest extends CamelTestSupport {

    private int port1;
    private String uri1;
    private int port2;
    private String uri2;

    @Test
    public void testProducer() throws Exception {
        Person person = new Person("David", "Greco");
        
        getMockEndpoint("mock:test1").expectedBodiesReceived(person);
        getMockEndpoint("mock:test1").expectedBodiesReceived(person);
        
        //act
        template.requestBodyAndHeader("direct:input1", person, "testHeading", "value");
        template.requestBodyAndHeader("direct:input2", person, "testHeading", "value");
       
        assertMockEndpointsSatisfied();
    }

   
    
   

    @Override
    @Before
    public void setUp() throws Exception {
        port1 = AvailablePortFinder.getNextAvailable(23500);
        port2 = AvailablePortFinder.getNextAvailable(23510);
        uri1 = "cometd://127.0.0.1:" + port1 + "/service/test?baseResource=file:./target/test-classes/webapp&"
                + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

        uri2 = "cometd://127.0.0.1:" + port2 + "/service/test?baseResource=file:./target/test-classes/webapp&"
            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:input1").to(uri1);

                from(uri1).to("mock:test1");
                
                from("direct:input2").to(uri2);
                
                from(uri2).to("mock:test2");
            }
        };
    }

    public static class Person {

        private String name;
        private String surname;

        Person(String name, String surname) {
            this.name = name;
            this.surname = surname;
        }

        public String getName() {
            return name;
        }

        public String getSurname() {
            return surname;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSurname(String surname) {
            this.surname = surname;
        }
    }
}

