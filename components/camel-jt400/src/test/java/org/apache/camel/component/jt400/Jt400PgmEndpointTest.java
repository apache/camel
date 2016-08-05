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

/**
 * Test case for {@link Jt400Endpoint}
 */
public class Jt400PgmEndpointTest extends Jt400TestSupport {

    private static final String USER = "USER";
    private static final String HOST = "host";
    private static final String PASSWORD = "password";
    private static final String PGM = "/qsys.lib/library.lib/prog.pgm";

    private Jt400Endpoint endpoint;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        endpoint = (Jt400Endpoint) resolveMandatoryEndpoint("jt400://" + USER + ":" + PASSWORD
                + "@" + HOST + PGM
                + "?connectionPool=#mockPool&guiAvailable=true&format=binary&outputFieldsIdx=1,2&fieldsLength=10,512,255");
    }

    /**
     * Check that the AS/400 connection is correctly configured for the URL
     */
    @Test
    public void testSystemConfiguration() {
        assertEquals(USER, endpoint.getUserID());
        assertEquals(HOST, endpoint.getSystemName());
        assertEquals(PGM, endpoint.getObjectPath());
        assertTrue(endpoint.isGuiAvailable());
        assertEquals(Jt400Configuration.Format.binary, endpoint.getFormat());
        assertEquals(10, endpoint.getOutputFieldLength(0));
        assertEquals(512, endpoint.getOutputFieldLength(1));
        assertEquals(255, endpoint.getOutputFieldLength(2));
        assertEquals(false, endpoint.isFieldIdxForOuput(0));
        assertEquals(true, endpoint.isFieldIdxForOuput(1));
        assertEquals(true, endpoint.isFieldIdxForOuput(2));
    }
    
    @Test
    public void testToString() {
        assertEquals("jt400://USER:xxxxxx@host/qsys.lib/library.lib/prog.pgm?connectionPool=%23mockPool&fieldsLength=10%2C512%2C255&format=binary&guiAvailable=true&outputFieldsIdx=1%2C2",
                     endpoint.toString());
    }
}
