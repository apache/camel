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

import com.ibm.as400.access.AS400;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Jt400ConfigurationConnectionTest extends Jt400TestSupport {

    private Jt400Configuration jt400Configuration;
    private AS400 connection;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        jt400Configuration = new Jt400Configuration("jt400://USER:password@host/QSYS.LIB/LIBRARY.LIB/QUEUE.DTAQ", getConnectionPool());
        jt400Configuration.setCcsid(37);
        connection = jt400Configuration.getConnection();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            jt400Configuration.releaseConnection(connection);
        }
        super.tearDown();
    }

    @Test
    public void testSystemName() {
        assertEquals("host", connection.getSystemName());
    }

    @Test
    public void testUserId() {
        assertEquals("USER", connection.getUserId());
    }

    @Test
    public void testCssid() {
        assertEquals(37, connection.getCcsid());
    }

    @Test
    public void testGuiAvailable() {
        assertFalse(connection.isGuiAvailable());
    }

}
