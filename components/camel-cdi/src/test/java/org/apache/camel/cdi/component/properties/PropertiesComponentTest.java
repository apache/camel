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
package org.apache.camel.cdi.component.properties;

import org.apache.camel.cdi.CdiContextTestSupport;
import org.junit.Test;

/**
 * Verifies behavior of properties component in CDI environment.
 */
public class PropertiesComponentTest extends CdiContextTestSupport {

    @Test
    public void shouldUseCdiProperties() throws Exception {
        assertTrue(context.getComponent("properties") instanceof CdiPropertiesComponent);
        String resolved = context.resolvePropertyPlaceholders("d{{directEndpoint}}b");

        assertEquals("ddirect:injectb", resolved);
        resolved = context.resolvePropertyPlaceholders("{{directEndpoint}}_{{directEndpoint}}");

        assertEquals("direct:inject_direct:inject", resolved);
    }

    @Test
    public void testNullArgument() throws Exception {
        assertNull(context.resolvePropertyPlaceholders(null));
    }

    @Test
    public void testTextWithNoPlaceholder() throws Exception {
        assertEquals("IamAnonymous", context.resolvePropertyPlaceholders("IamAnonymous"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownNestedPlaceholder() throws Exception {
        context.resolvePropertyPlaceholders("{{IamAnonymous}}");
    }

}
