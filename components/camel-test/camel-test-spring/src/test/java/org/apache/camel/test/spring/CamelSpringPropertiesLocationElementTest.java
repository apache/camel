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
package org.apache.camel.test.spring;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.PropertiesComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(CamelSpringRunner.class)
@BootstrapWith(CamelTestContextBootstrapper.class)
@ContextConfiguration()
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CamelSpringPropertiesLocationElementTest {
    @Autowired
    protected CamelContext context;
    @Produce
    private ProducerTemplate producer;
    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void testPropertiesLocationElement() throws Exception {
        mock.expectedHeaderReceived("property-1", "property-value-1");
        mock.expectedHeaderReceived("property-2", "property-value-2");
        mock.expectedHeaderReceived("property-3", "property-value-3");

        PropertiesComponent pc = (PropertiesComponent) context.getPropertiesComponent();
        assertNotNull("Properties component not defined", pc);

        List<String> locations = pc.getLocations();

        assertNotNull(locations);
        assertEquals("Properties locations", 4, locations.size());

        producer.sendBody("direct:start", null);

        mock.assertIsSatisfied();
    }
}
