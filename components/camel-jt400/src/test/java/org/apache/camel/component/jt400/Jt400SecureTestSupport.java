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

import com.ibm.as400.access.AS400ConnectionPool;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.After;
import org.junit.Before;

/**
 * Useful base class for JT400 secured component unit tests. It creates a mock
 * secured connection pool, registers it under the ID {@code "mockPool"} and releases it
 * after the test runs.
 */
public abstract class Jt400SecureTestSupport extends CamelTestSupport {

    private AS400ConnectionPool connectionPool;

    protected Jt400SecureTestSupport() {
    }

    @Before
    public void setUp() throws Exception {
        connectionPool = new MockAS400SecureConnectionPool();
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (connectionPool != null) {
            connectionPool.close();
        }
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        ObjectHelper.notNull(connectionPool, "connectionPool");
        JndiRegistry registry = super.createRegistry();
        registry.bind("mockPool", connectionPool);
        return registry;
    }

    /**
     * Returns the mock connection pool.
     *
     * @return the mock connection pool
     */
    public AS400ConnectionPool getConnectionPool() {
        return connectionPool;
    }

}
