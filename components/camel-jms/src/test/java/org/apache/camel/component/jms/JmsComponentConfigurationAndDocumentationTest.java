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
package org.apache.camel.component.jms;

import org.apache.camel.ComponentConfiguration;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JmsComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        JmsComponent comp = context.getComponent("jms", JmsComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("jms:queue:foo?replyTo=bar");

        assertEquals("bar", conf.getParameter("replyTo"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);
    }

    @Test
    public void testExplainComponentJson() throws Exception {
        String json = context.explainComponentJson("jms", false);
        assertNotNull(json);

        log.info(json);
        assertTrue(json.contains("\"syntax\": \"jms:destinationType:destinationName\""));
    }

    @Test
    public void testExplainEndpointJson() throws Exception {
        String json = context.explainEndpointJson("jms:queue:foo?replyTo=bar", false);
        assertNotNull(json);
        log.info(json);
        assertTrue(json.contains("\"syntax\": \"jms:destinationType:destinationName\""));
        assertTrue(json.contains("\"destinationType\": { \"kind\": \"path\", \"type\": \"string\", \"javaType\": \"java.lang.String\""
                + ", \"deprecated\": \"false\", \"value\": \"queue\", \"defaultValue\": \"queue\""));
        assertTrue(json.contains("\"destinationName\": { \"kind\": \"path\", \"required\": \"true\", \"type\": \"string\""
                + ", \"javaType\": \"java.lang.String\", \"deprecated\": \"false\", \"value\": \"foo\""));
        assertTrue(json.contains("\"replyTo\": { \"kind\": \"parameter\", \"label\": \"consumer\", \"type\": \"string\""
                + ", \"javaType\": \"java.lang.String\", \"deprecated\": \"false\", \"value\": \"bar\""));

        json = context.explainEndpointJson("jms:foo?replyTo=bar", false);
        assertNotNull(json);
        log.info(json);
        assertTrue(json.contains("\"syntax\": \"jms:destinationType:destinationName\""));
        assertTrue(json.contains("\"destinationType\": { \"kind\": \"path\", \"type\": \"string\", \"javaType\": \"java.lang.String\""
                + ", \"deprecated\": \"false\", \"defaultValue\": \"queue\""));
        assertTrue(json.contains("\"destinationName\": { \"kind\": \"path\", \"required\": \"true\", \"type\": \"string\""
                + ", \"javaType\": \"java.lang.String\", \"deprecated\": \"false\", \"value\": \"foo\""));
        assertTrue(json.contains("\"replyTo\": { \"kind\": \"parameter\", \"label\": \"consumer\", \"type\": \"string\""
                + ", \"javaType\": \"java.lang.String\", \"deprecated\": \"false\", \"value\": \"bar\""));
    }

}
