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
package org.apache.camel.test.blueprint.component.rest;

import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.rest.PathDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class FromRestGetTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/component/rest/FromRestGetTest.xml";
    }

    @Test
    public void testFromRestModel() {
        assertEquals(2 + 3, context.getRoutes().size());

        RestDefinition rest = context.getRestDefinitions().get(0);
        assertNotNull(rest);

        PathDefinition path = rest.getPaths().get(0);
        assertNotNull(0);
        assertEquals("/say", path.getUri());

        assertEquals("/hello", path.getVerbs().get(0).getUri());
        ToDefinition to = assertIsInstanceOf(ToDefinition.class, path.getVerbs().get(0).getOutputs().get(0));
        assertEquals("direct:hello", to.getUri());

        assertEquals("/bye", path.getVerbs().get(1).getUri());
        to = assertIsInstanceOf(ToDefinition.class, path.getVerbs().get(1).getOutputs().get(0));
        assertEquals("direct:bye", to.getUri());

        assertEquals(null, path.getVerbs().get(2).getUri());
    }

}
