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
package org.apache.camel.component.beanstalk;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class UriConfigurationTest extends Assert {

    @Test
    public void testComponentConfiguration() throws Exception {
        CamelContext context = new DefaultCamelContext();

        BeanstalkComponent comp = context.getComponent("beanstalk", BeanstalkComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("beanstalk:tube?command=put");

        assertEquals("put", conf.getParameter("command"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"command\": { \"kind\": \"parameter\", \"group\": \"common\", \"type\": \"string\""));
        assertTrue(json.contains("\"jobTimeToRun\": { \"kind\": \"parameter\", \"group\": \"common\", \"type\": \"integer\""));
        assertTrue(json.contains("\"awaitJob\": { \"kind\": \"parameter\", \"group\": \"consumer\", \"label\": \"consumer\", \"type\": \"boolean\""));
    }

    @Test
    public void testComponentDocumentation() throws Exception {
        CamelContext context = new DefaultCamelContext();
        String html = context.getComponentDocumentation("beanstalk");
        assertNotNull("Should have found some auto-generated HTML", html);
    }
}
