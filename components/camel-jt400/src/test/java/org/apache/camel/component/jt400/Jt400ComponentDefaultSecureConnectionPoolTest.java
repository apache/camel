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

import com.ibm.as400.access.AS400ConnectionPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class Jt400ComponentDefaultSecureConnectionPoolTest extends Jt400TestSupport {
    private Jt400Component component;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        component = new Jt400Component();
        component.setCamelContext(context);
        try {
            // Use an invalid object type so that endpoints are never created
            // and actual connections are never requested
            component.createEndpoint("jt400://user:password@host/qsys.lib/library.lib/program.xxx?secured=true");
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            /* Expected */
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (component != null) {
            component.stop();
        }
    }

    @Test
    public void testDefaultConnectionPoolIsCreated() {
        assertNotNull(component.getConnectionPool());
    }

    /**
     * Note: white-box testing.
     */
    @Test
    public void testDefaultConnectionPoolIsOfExpectedType() {
        assertEquals(AS400ConnectionPool.class, component.getConnectionPool().getClass());
    }
}
