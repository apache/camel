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
package org.apache.camel.component.xslt;

import java.util.Map;

import net.sf.saxon.Configuration;
import org.apache.camel.Endpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SaxonXsltEndpointConfigurationTest extends CamelSpringTestSupport {
    @Test
    public void testConfiguration() throws Exception {
        Configuration configuration = context.getRegistry().lookupByNameAndType("saxon-configuration", Configuration.class);
        Map<String, Object> properties = context.getRegistry().lookupByNameAndType("saxon-properties", Map.class);
        XsltComponent component = context.getComponent("xslt", XsltComponent.class);
        XsltEndpoint endpoint = null;

        assertNotNull(configuration);
        assertNotNull(properties);

        for (Endpoint ep : context.getEndpoints()) {
            if (ep instanceof XsltEndpoint) {
                endpoint = (XsltEndpoint)ep;
                break;
            }
        }

        assertNotNull(component);
        assertNotNull(endpoint);
        assertNull(component.getSaxonConfiguration());
        assertTrue(component.getSaxonConfigurationProperties().isEmpty());
        assertNotNull(endpoint.getSaxonConfiguration());
        assertNotNull(endpoint.getSaxonConfigurationProperties());
        assertEquals(configuration, endpoint.getSaxonConfiguration());
        assertEquals(properties, endpoint.getSaxonConfigurationProperties());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/xslt/SaxonXsltEndpointConfigurationTest.xml");
    }
}