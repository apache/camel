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
package org.apache.camel.dataformat.castor;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class WhitelistTest extends CamelTestSupport {

    @Test
    public void testDeny() throws Exception {
        final String stuff = "<x xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:java=\"http://java.sun.com\""
            + " xsi:type=\"java:org.springframework.beans.factory.config.PropertyPathFactoryBean\">"
            + "<target-bean-name>ldap://localhost:1389/obj</target-bean-name><property-path>foo</property-path>"
            + "<bean-factory xsi:type=\"java:org.springframework.jndi.support.SimpleJndiBeanFactory\">"
            + "<shareable-resource>ldap://localhost:1389/obj</shareable-resource></bean-factory></x>";

        try {
            template.sendBody("direct:unmarshal", stuff);
            fail("Should throw an error");
        } catch (Exception e) {
            IllegalAccessException iae = assertIsInstanceOf(IllegalAccessException.class, e.getCause().getCause());
            assertNotNull(iae);
            assertTrue(iae.getMessage().startsWith("Not allowed to create class of type: class org.springframework.beans.factory.config.PropertyPathFactoryBean"));
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        CastorDataFormat castor = new CastorDataFormat();
        // note that whitelist is enabled by default
        // castor.setWhitlistEnabled(true);
        // and that everything is denied by default
        // so you would need to configure allow to enable safe classes to be loaded
        // castor.setDeniedUnmarshallObjects("org.spring.*");

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:unmarshal").unmarshal(castor).to("mock:unmarshal");
            }
        };
    }
}
