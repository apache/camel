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
package org.apache.camel.component.jt400;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.jt400.Jt400DataQueueEndpoint.Format;

/**
 * Test case for {@link Jt400DataQueueEndpoint}
 */
public class Jt400DataQueueEndpointTest extends ContextTestSupport {

    private Jt400DataQueueEndpoint endpoint;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        endpoint = (Jt400DataQueueEndpoint)resolveMandatoryEndpoint("jt400://user:password@host/qsys.lib/library.lib/queue.dtaq?ccsid=500&format=binary");
    }

    /**
     * Check that the AS/400 connection is correctly configured for the URL
     */
    public void testSystemConfiguration() {
        assertEquals("USER", endpoint.getSystem().getUserId());
        assertEquals("host", endpoint.getSystem().getSystemName());
        assertEquals(500, endpoint.getSystem().getCcsid());
        assertEquals(Format.binary, endpoint.getFormat());
    }
}
