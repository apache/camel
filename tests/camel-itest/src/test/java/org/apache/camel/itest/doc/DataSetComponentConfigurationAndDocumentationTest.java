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
import org.apache.camel.component.dataset.DataSetComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class DataSetComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        DataSetComponent comp = context.getComponent("dataset", DataSetComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("dataset:foo?minRate=3&produceDelay=33&consumeDelay=333&preloadSize=3333&initialDelay=33333&disableDataSetIndex=true");

        assertEquals("Unexpected endpoint configuration value for minRate", "3", conf.getParameter("minRate"));
        assertEquals("Unexpected endpoint configuration value for produceDelay", "33", conf.getParameter("produceDelay"));
        assertEquals("Unexpected endpoint configuration value for consumeDelay", "333", conf.getParameter("consumeDelay"));
        assertEquals("Unexpected endpoint configuration value for preloadSize", "3333", conf.getParameter("preloadSize"));
        assertEquals("Unexpected endpoint configuration value for initialDelay", "33333", conf.getParameter("initialDelay"));
        assertEquals("Unexpected endpoint configuration value for disableDataSetIndex", "true", conf.getParameter("disableDataSetIndex"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"name\": { \"kind\": \"path\", \"displayName\": \"Name\", \"group\": \"common\", \"required\": true, \"type\""));
        assertTrue(json.contains("\"retainFirst\": { \"kind\": \"parameter\", \"displayName\": \"Retain First\", \"group\": \"producer\", \"label\": \"producer\", \"type\": \"integer"));
    }

}
