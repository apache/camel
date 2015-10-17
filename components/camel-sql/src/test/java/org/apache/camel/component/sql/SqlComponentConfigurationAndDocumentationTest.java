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
package org.apache.camel.component.sql;

import org.apache.camel.ComponentConfiguration;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SqlComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        SqlComponent comp = context.getComponent("sql", SqlComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("sql:select?dataSourceRef=jdbc/myDataSource&allowNamedParameters=true&consumer.delay=5000");

        assertEquals("jdbc/myDataSource", conf.getParameter("dataSourceRef"));
        assertEquals("true", conf.getParameter("allowNamedParameters"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);
    }

    @Test
    public void testExplainEndpoint() throws Exception {
        String json = context.explainEndpointJson("sql:select?dataSourceRef=jdbc/myDataSource&allowNamedParameters=true&onConsume=foo", true);
        assertNotNull(json);

        assertTrue(json.contains("\"onConsumeBatchComplete\": { \"kind\": \"parameter\", \"label\": \"consumer\", \"type\": \"string\""));
        assertTrue(json.contains("\"parametersCount\": { \"kind\": \"parameter\", \"label\": \"producer,advanced\", \"type\": \"integer\""));
        assertTrue(json.contains("\"onConsume\": { \"kind\": \"parameter\", \"label\": \"consumer\", \"type\": \"string\", \"javaType\": \"java.lang.String\", \"deprecated\": \"false\", "
                + "\"value\": \"foo\""));
    }

}
