/*
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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SedaDefaultUnboundedQueueSizeTest extends ContextTestSupport {

    @Test
    public void testSedaDefaultUnboundedQueueSize() throws Exception {
        SedaEndpoint seda = context.getEndpoint("seda:foo", SedaEndpoint.class);
        assertEquals(0, seda.getQueue().size());

        for (int i = 0; i < 1000; i++) {
            template.sendBody("seda:foo", "Message " + i);
        }

        assertEquals(1000, seda.getQueue().size());
    }

    @Test
    public void testSedaDefaultBoundedQueueSize() throws Exception {
        SedaEndpoint seda = context.getEndpoint("seda:foo?size=500", SedaEndpoint.class);
        assertEquals(0, seda.getQueue().size());

        for (int i = 0; i < 500; i++) {
            template.sendBody("seda:foo", "Message " + i);
        }

        assertEquals(500, seda.getQueue().size());

        // sending one more hit the limit
        Exception e = assertThrows(Exception.class, () -> template.sendBody("seda:foo", "Message overflow"),
                "Should thrown an exception");
        assertIsInstanceOf(IllegalStateException.class, e.getCause());
    }

}
