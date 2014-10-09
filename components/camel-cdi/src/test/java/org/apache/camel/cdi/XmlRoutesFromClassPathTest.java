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
package org.apache.camel.cdi;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RoutesDefinition;
import org.junit.Test;

/**
 * Checks we can load XML routes from the classpath and use then with CDI
 */
public class XmlRoutesFromClassPathTest extends CdiTestSupport {
    @Inject
    @Mock
    MockEndpoint results;

    @Inject
    @Uri("direct:start")
    ProducerTemplate producer;

    Object[] expectedBodies = {"body:1", "body:2"};
    
    @Produces
    @ContextName
    public RoutesDefinition createRoutes() throws Exception {
        return RoutesXml.loadRoutesFromClasspath(new CdiCamelContext(), "routes.xml");
    }
    
    @Test
    public void xmlRoutesWorkOnClassPath() throws Exception {
        assertNotNull("results not injected", results);
        assertNotNull("producer not injected", producer);

        results.expectedBodiesReceived(expectedBodies);

        for (Object body : expectedBodies) {
            producer.sendBody(body);
        }

        results.assertIsSatisfied();
    }
}
