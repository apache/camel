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

import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.component.seda.SedaEndpoint;
import org.junit.Test;

public class EndpointPropertyTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/EndpointPropertyTest.xml";
    }

    @Test
    public void testEndpointProperty() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        template.sendBody("ref:foo", "Hello World");
        template.sendBody("ref:bar", "Bye World");
        assertMockEndpointsSatisfied();

        BlueprintCamelContext blue = context().adapt(BlueprintCamelContext.class);

        SedaEndpoint foo = (SedaEndpoint) blue.getBlueprintContainer().getComponentInstance("foo");
        assertNotNull(foo);
        assertEquals(100, foo.getSize());
        assertEquals(5000, foo.getPollTimeout());
        assertEquals(true, foo.isBlockWhenFull());
        assertEquals("seda://foo?blockWhenFull=true&pollTimeout=5000&size=100", foo.getEndpointUri());

        SedaEndpoint bar = (SedaEndpoint) blue.getBlueprintContainer().getComponentInstance("bar");
        assertNotNull(bar);
        assertEquals(200, bar.getSize());
        assertEquals("seda://bar?size=200", bar.getEndpointUri());
    }

}
