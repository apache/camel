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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit testing for using a CometdProducer and a CometdConsumer
 */
public class SslContextParametersInUriCometdProducerConsumerTest extends CamelTestSupport {

    private int port;
    private String uri;
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("jsse/localhost.ks");
        ksp.setPassword("changeit");
        
        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(ksp);
        
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        
        JndiRegistry registry = super.createRegistry();
        registry.bind("sslContextParameters", sslContextParameters);
        return registry;
    }

    @Test
    public void testProducer() throws Exception {
        Person person = new Person("David", "Greco");
        template.requestBody("direct:input", person);
        MockEndpoint ep = context.getEndpoint("mock:test", MockEndpoint.class);
        List<Exchange> exchanges = ep.getReceivedExchanges();
        for (Exchange exchange : exchanges) {
            Person person1 = (Person) exchange.getIn().getBody();
            assertEquals("David", person1.getName());
            assertEquals("Greco", person1.getSurname());
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable(23500);
        uri = "cometds://127.0.0.1:" + port + "/service/test?baseResource=file:./target/test-classes/webapp&"
                + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // setup SSL on the component
                CometdComponent cometds = context.getComponent("cometds", CometdComponent.class);
                cometds.setSslContextParameters(context.getRegistry().lookupByNameAndType("sslContextParameters", SSLContextParameters.class));

                from("direct:input").to(uri);

                from(uri).to("mock:test");
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
