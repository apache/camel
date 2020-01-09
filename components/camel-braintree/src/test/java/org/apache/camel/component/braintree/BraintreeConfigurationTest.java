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
package org.apache.camel.component.braintree;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.braintreegateway.BraintreeGateway;
import org.apache.camel.component.braintree.internal.BraintreeLogHandler;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BraintreeConfigurationTest {

    @Test
    public void testLoggerConfiguration() {
        BraintreeConfiguration configuration = createBraintreeConfiguration();
        configuration.setHttpLogLevel(Level.WARNING);

        BraintreeGateway braintreeGateway = configuration.newBraintreeGateway();
        Logger logger = braintreeGateway.getConfiguration().getLogger();
        assertEquals(Level.WARNING, logger.getLevel());
        assertEquals(1, logger.getHandlers().length);
        assertTrue(logger.getHandlers()[0] instanceof BraintreeLogHandler);
    }

    @Test
    public void testBraintreeLogHandlerDisabled() {
        BraintreeConfiguration configuration = createBraintreeConfiguration();
        configuration.setLogHandlerEnabled(false);

        BraintreeGateway braintreeGateway = configuration.newBraintreeGateway();
        Logger logger = braintreeGateway.getConfiguration().getLogger();
        assertEquals(0, logger.getHandlers().length);
    }

    private BraintreeConfiguration createBraintreeConfiguration() {
        BraintreeConfiguration configuration = new BraintreeConfiguration();
        configuration.setEnvironment("SANDBOX");
        configuration.setMerchantId("dummy-merchant-id");
        configuration.setPublicKey("dummy-public-key");
        configuration.setPrivateKey("dummy-private-key");
        return configuration;
    }

}
