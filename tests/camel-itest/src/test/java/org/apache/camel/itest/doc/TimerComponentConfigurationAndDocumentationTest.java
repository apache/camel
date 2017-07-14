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
import org.apache.camel.component.timer.TimerComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class TimerComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        TimerComponent comp = context.getComponent("timer", TimerComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("timer:foo?period=2000");

        assertEquals("2000", conf.getParameter("period"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"timerName\": { \"kind\": \"path\", \"displayName\": \"Timer Name\", \"group\": \"consumer\", \"required\": true"));
        assertTrue(json.contains("\"delay\": { \"kind\": \"parameter\", \"displayName\": \"Delay\", \"group\": \"consumer\", \"type\": \"integer\""));
        assertTrue(json.contains("\"timer\": { \"kind\": \"parameter\", \"displayName\": \"Timer\", \"group\": \"advanced\", \"label\": \"advanced\""));
    }

}
