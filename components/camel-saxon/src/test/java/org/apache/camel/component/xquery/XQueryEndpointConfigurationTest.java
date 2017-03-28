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
package org.apache.camel.component.xquery;

import java.util.Map;

import net.sf.saxon.Configuration;
import org.apache.camel.Endpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XQueryEndpointConfigurationTest extends CamelSpringTestSupport {
    @Test
    public void testConfiguration() throws Exception {
        Configuration configuration = context.getRegistry().lookupByNameAndType("saxon-configuration", Configuration.class);
        Map<String, Object> properties = context.getRegistry().lookupByNameAndType("saxon-properties", Map.class);
        XQueryComponent component = context.getComponent("xquery", XQueryComponent.class);
        XQueryEndpoint endpoint = null;

        assertNotNull(configuration);
        assertNotNull(properties);

        for (Endpoint ep : context.getEndpoints()) {
            if (ep instanceof XQueryEndpoint) {
                endpoint = (XQueryEndpoint)ep;
                break;
            }
        }

        assertNotNull(component);
        assertNotNull(endpoint);
        assertNull(component.getConfiguration());
        assertTrue(component.getConfigurationProperties().isEmpty());
        assertNotNull(endpoint.getConfiguration());
        assertNotNull(endpoint.getConfigurationProperties());
        assertEquals(configuration, endpoint.getConfiguration());
        assertEquals(properties, endpoint.getConfigurationProperties());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/xquery/XQueryEndpointConfigurationTest.xml");
    }
}