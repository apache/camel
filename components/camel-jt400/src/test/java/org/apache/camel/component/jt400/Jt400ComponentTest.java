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

import java.util.HashMap;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test case for {@link Jt400Component}
 */
@SuppressWarnings("unchecked")
public class Jt400ComponentTest extends Assert {

    private Jt400Component component;

    @Before
    public void setUp() throws Exception {        
        component = new Jt400Component();
    }

    /**
     * Test creation of a {@link Jt400DataQueueEndpoint} for Datq
     */
    @Test
    public void testCreateDatqEndpoint() throws Exception {
        Endpoint endpoint = component
            .createEndpoint("jt400://user:password@host/qsys.lib/library.lib/queue.dtaq",
                            "/user:password@host/qsys.lib/library.lib/queue.dtaq", new HashMap());
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Jt400DataQueueEndpoint);
    }

    /**
     * Test creation of a {@link Jt400DataQueueEndpoint} for pgm calls
     */
    @Test
    public void testCreatePgmEndpoint() throws Exception {
        Endpoint endpoint = component
            .createEndpoint("jt400://user:password@host/qsys.lib/library.lib/queue.pgm",
                            "/user:password@host/qsys.lib/library.lib/queue.pgm", new HashMap());
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof Jt400PgmEndpoint);
    }

    /**
     * Test exception when trying to access any other object type on AS/400
     */
    @Test
    public void testCreateEndpointForOtherObjectType() throws Exception {
        try {
            component.createEndpoint("jt400://user:password@host/qsys.lib/library.lib/program.xxx",
                                     "/user:password@host/qsys.lib/library.lib/program.xxx", new HashMap());
            fail("Exception should been thrown when trying to create an endpoint for an unsupported object type");
        } catch (CamelException e) {
            // this is just what we expected
        }
    }

}
