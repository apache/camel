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

import org.apache.camel.component.seda.SedaComponent;
import org.junit.Test;

public class BlueprintComponentPropertyPlaceholderTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/BlueprintComponentPropertyPlaceholderTest.xml";
    }

    @Test
    public void testComponentPropertyPlaceholder() throws Exception {
        SedaComponent myseda = (SedaComponent) context.getComponent("myseda", false);
        assertNotNull(myseda);
        assertEquals(2, myseda.getQueueSize());

        // test that the custom component works

        int before = myseda.getQueueReference("myseda://foo").getQueue().size();
        assertEquals(0, before);

        template.sendBody("direct:start", "Hello World");

        int after = myseda.getQueueReference("myseda://foo").getQueue().size();
        assertEquals(1, after);
    }

}
