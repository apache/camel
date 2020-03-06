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
package org.apache.camel.component.jt400;

import org.junit.Before;
import org.junit.Test;

/**
 * Test case for {@link Jt400Endpoint}
 */
public class Jt400EndpointTest extends Jt400TestSupport {

    private Jt400Endpoint endpoint;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        endpoint = (Jt400Endpoint) resolveMandatoryEndpoint("jt400://user:password@host/qsys.lib/library.lib/queue.dtaq?ccsid=500&format=binary&guiAvailable=true&connectionPool=#mockPool");
    }

    /**
     * Check that the AS/400 connection is correctly configured for the URL
     */
    @Test
    public void testSystemConfiguration() {
        assertEquals("USER", endpoint.getSystem().getUserId());
        assertEquals("host", endpoint.getSystem().getSystemName());
        assertEquals(500, endpoint.getSystem().getCcsid());
        assertEquals(Jt400Configuration.Format.binary, endpoint.getFormat());
        assertTrue(endpoint.getSystem().isGuiAvailable());
    }

}
