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

import org.apache.camel.component.jt400.Jt400DataQueueEndpoint.Format;
import org.junit.Before;
import org.junit.Test;

public class Jt400EndpointTest extends Jt400TestSupport {

    private Jt400Endpoint jt400Endpoint;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        jt400Endpoint = new Jt400Endpoint("jt400://USER:password@host/QSYS.LIB/LIBRARY.LIB/QUEUE.DTAQ", getConnectionPool());
    }

    @Test
    public void testSystemName() {
        assertEquals("host", jt400Endpoint.getSystemName());
    }

    @Test
    public void testUserID() {
        assertEquals("USER", jt400Endpoint.getUserID());
    }

    @Test
    public void testPassword() {
        assertEquals("password", jt400Endpoint.getPassword());
    }

    @Test
    public void testObjectPath() {
        assertEquals("/QSYS.LIB/LIBRARY.LIB/QUEUE.DTAQ", jt400Endpoint.getObjectPath());
    }

    @Test
    public void testDefaultCcsid() {
        assertEquals(-1, jt400Endpoint.getCssid());
    }

    @Test
    public void testDefaultFormat() {
        assertEquals(Format.text, jt400Endpoint.getFormat());
    }

    @Test
    public void testDefaultGuiAvailable() {
        assertEquals(false, jt400Endpoint.isGuiAvailable());
    }

}
