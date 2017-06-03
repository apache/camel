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
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class LogComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        LogComponent comp = context.getComponent("log", LogComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("log:foo?level=DEBUG");

        assertEquals("DEBUG", conf.getParameter("level"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"loggerName\": { \"kind\": \"path\", \"displayName\": \"Logger Name\", \"group\": \"producer\", \"required\": true"));
        assertTrue(json.contains("\"level\": { \"kind\": \"parameter\", \"displayName\": \"Level\", \"group\": \"producer\", \"type\": \"string\""));
        assertTrue(json.contains("\"showBody\": { \"kind\": \"parameter\", \"displayName\": \"Show Body\", \"group\": \"formatting\", \"label\": \"formatting\""));
    }

}
