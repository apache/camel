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
package org.apache.camel.itest.cdi;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(Arquillian.class)
public class CamelCdiTest {

    @Inject
    CamelContext camelContext;

    @Inject
    @Uri("seda:a")
    ProducerTemplate producer;
    
    @Deployment
    public static JavaArchive createDeployment() {
        return Maven.configureResolver().workOffline()
            .loadPomFromFile("pom.xml")
            .resolve("org.apache.camel:camel-cdi")
            .withoutTransitivity()
            .asSingle(JavaArchive.class)
            .addClasses(
                MyRoutes.class
            );
    }

    @Test
    public void testSendMessages() throws Exception {
        assertNotNull(camelContext);

        MockEndpoint b = camelContext.getEndpoint("mock:b", MockEndpoint.class);
        b.expectedBodiesReceived("Hello World");

        producer.sendBody("Hello World");

        b.assertIsSatisfied();
    }

}
