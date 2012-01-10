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
package org.apache.camel.component.gae.task;

import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.gae.task.GTaskTestUtils.createEndpoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GTaskEndpointTest {

    private static final String AMP = "&";
    
    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testPropertiesDefault() throws Exception {
        GTaskEndpoint endpoint = createEndpoint("gtask:myqueue");
        assertEquals("worker", endpoint.getWorkerRoot());
        assertTrue(endpoint.getOutboundBinding().getClass().equals(GTaskBinding.class));
    }
    
    @Test
    public void testPropertiesCustom() throws Exception {
        StringBuilder buffer = new StringBuilder("gtask:myqueue")
            .append("?").append("outboundBindingRef=#customBinding")
            .append(AMP).append("inboundBindingRef=#customBinding")
            .append(AMP).append("workerRoot=test");
        GTaskEndpoint endpoint = createEndpoint(buffer.toString());
        assertEquals("test", endpoint.getWorkerRoot());
        assertFalse(endpoint.getOutboundBinding().getClass().equals(GTaskBinding.class));
        assertTrue(endpoint.getOutboundBinding() instanceof GTaskBinding);
        assertEquals("gtask:/myqueue", endpoint.getEndpointUri());
    }

}
