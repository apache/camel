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
package org.apache.camel.itest.doc;

import org.apache.camel.ComponentConfiguration;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class MockComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        MockComponent comp = context.getComponent("mock", MockComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("mock:foo?retainFirst=10");

        assertEquals("10", conf.getParameter("retainFirst"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"name\": { \"kind\": \"path\", \"displayName\": \"Name\", \"group\": \"producer\", \"required\": true"));
        assertTrue(json.contains("\"expectedCount\": { \"kind\": \"parameter\", \"displayName\": \"Expected Count\", \"group\": \"producer\", \"label\": \"producer\""));
        assertTrue(json.contains("\"retainFirst\": { \"kind\": \"parameter\", \"displayName\": \"Retain First\", \"group\": \"producer\", \"label\": \"producer\""));
    }

    @Test
    public void testEndpointExplain() throws Exception {
        String json = context.explainEndpointJson("mock:foo?retainFirst=10", true);
        assertNotNull(json);

        assertTrue(json.contains("\"retainFirst\": { \"kind\": \"parameter\", \"group\": \"producer\", \"label\": \"producer\", \"type\": \"integer\","
            + " \"javaType\": \"int\", \"deprecated\": \"false\", \"secret\": \"false\", \"value\": \"10\""));
    }

}
