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
package org.apache.camel.component.weather;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class WeatherComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        WeatherComponent comp = context.getComponent("weather", WeatherComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("weather:foo?location=Madrid,Spain");

        assertEquals("Madrid,Spain", conf.getParameter("location"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"location\": { \"kind\": \"parameter\", \"type\": \"string\", \"javaType\": \"java.lang.String\""));
        assertTrue(json.contains("\"mode\": { \"kind\": \"parameter\", \"type\": \"string\", \"javaType\": \"org.apache.camel.component.weather.WeatherMode\""
                + ", \"enum\": [ \"HTML\", \"JSON\", \"XML\" ]"));
    }

    @Test
    public void testComponentDocumentation() throws Exception {
        CamelContext context = new DefaultCamelContext();
        String html = context.getComponentDocumentation("weather");
        assertNotNull("Should have found some auto-generated HTML", html);
    }

}
