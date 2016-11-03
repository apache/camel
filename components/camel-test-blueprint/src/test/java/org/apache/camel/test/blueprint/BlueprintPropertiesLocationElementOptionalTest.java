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

package org.apache.camel.test.blueprint;

import java.util.List;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesLocation;
import org.junit.Test;

public class BlueprintPropertiesLocationElementOptionalTest extends CamelBlueprintTestSupport {
    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/properties-location-element-optional-test.xml";
    }

    @Test
    public void testPropertiesLocationElement() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived("property-1", "property-value-1");
        mock.expectedHeaderReceived("property-2", "property-value-2");
        mock.expectedHeaderReceived("cm", "cm-value");

        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        assertNotNull("Properties component not defined", pc);

        List<PropertiesLocation> locations = pc.getLocations();

        assertNotNull(locations);
        assertEquals("Properties locations", 3, locations.size());

        template.sendBody("direct:start", null);

        mock.assertIsSatisfied();
    }
}
