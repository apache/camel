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

package org.apache.camel.test.infra.core;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default lifecycle manager suitable for most of the tests within Camel and end-user applications
 */
public class DefaultContextLifeCycleManager implements ContextLifeCycleManager {
    public static final int DEFAULT_SHUTDOWN_TIMEOUT = 10;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultContextLifeCycleManager.class);
    private int shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
    private boolean reset = true;

    /**
     * Creates a new instance of this class
     */
    public DefaultContextLifeCycleManager() {
    }

    /**
     * Creates a new instance of this class
     *
     * @param shutdownTimeout the shutdown timeout
     * @param reset           whether to reset any {@link MockEndpoint} after each test execution
     */
    public DefaultContextLifeCycleManager(int shutdownTimeout, boolean reset) {
        this.shutdownTimeout = shutdownTimeout;
        this.reset = reset;
    }

    @Override
    public void afterAll(CamelContext context) {
        if (context != null) {
            context.shutdown();
        } else {
            LOG.error(
                    "Cannot run the JUnit's afterAll because the context is null: a problem may have prevented the context from starting");
        }
    }

    @Override
    public void beforeAll(CamelContext context) {
        if (context != null) {
            context.getShutdownStrategy().setTimeout(shutdownTimeout);
        } else {
            LOG.error(
                    "Cannot run the JUnit's beforeAll because the context is null: a problem may have prevented the context from starting");
        }
    }

    @Override
    public void afterEach(CamelContext context) {
        if (reset) {
            MockEndpoint.resetMocks(context);
        }
    }

    @Override
    public void beforeEach(CamelContext context) {
        context.start();
    }
}
