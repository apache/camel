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
import org.apache.camel.component.controlbus.ControlBusComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ControlBusComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        ControlBusComponent comp = context.getComponent("controlbus", ControlBusComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("controlbus:route?routeId=bar&action=stop");

        assertEquals("bar", conf.getParameter("routeId"));
        assertEquals("stop", conf.getParameter("action"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"action\": { \"kind\": \"parameter\", \"displayName\": \"Action\", \"group\": \"producer\", \"type\": \"string\""));
        assertTrue(json.contains("\"async\": { \"kind\": \"parameter\", \"displayName\": \"Async\", \"group\": \"producer\", \"type\": \"boolean\""));
    }

}
