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

import org.junit.Before;
import org.junit.Test;

public class Jt400ConfigurationTest extends Jt400TestSupport {

    private Jt400Configuration jt400Configuration;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        jt400Configuration = new Jt400Configuration("jt400://USER:password@host/QSYS.LIB/LIBRARY.LIB/QUEUE.DTAQ", getConnectionPool());
    }

    @Test
    public void testDefaultSecured() {
        assertFalse(jt400Configuration.isSecured());
    }

    @Test
    public void testSystemName() {
        assertEquals("host", jt400Configuration.getSystemName());
    }

    @Test
    public void testUserID() {
        assertEquals("USER", jt400Configuration.getUserID());
    }

    @Test
    public void testPassword() {
        assertEquals("password", jt400Configuration.getPassword());
    }

    @Test
    public void testObjectPath() {
        assertEquals("/QSYS.LIB/LIBRARY.LIB/QUEUE.DTAQ", jt400Configuration.getObjectPath());
    }

    @Test
    public void testDefaultCcsid() {
        assertEquals(-1, jt400Configuration.getCssid());
    }

    @Test
    public void testDefaultFormat() {
        assertEquals(Jt400Configuration.Format.text, jt400Configuration.getFormat());
    }

    @Test
    public void testDefaultGuiAvailable() {
        assertEquals(false, jt400Configuration.isGuiAvailable());
    }

}
