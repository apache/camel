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
package org.apache.camel.component.mllp;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the MllpEndpoint class.
 */
public class MllpEndpointTest extends CamelTestSupport {

    /**
     * Assert that the default maxConcurrentConsumers property is correctly set on the endpoint instance.
     */
    @Test
    public void testCreateEndpointWithDefaultConfigurations() {
        MllpEndpoint mllpEndpoint = new MllpEndpoint("mllp://dummy", new MllpComponent(), new MllpConfiguration());

        assertEquals(5, mllpEndpoint.getConfiguration().getMaxConcurrentConsumers());
    }

    /**
     * Assert that the maxConcurrentConsumers property overridden in the MllpConfiguration object is correctly set on
     * the endpoint instance.
     */
    @Test
    public void testCreateEndpointWithCustomMaxConcurrentConsumers() {
        final int maxConcurrentConsumers = 10;
        MllpConfiguration mllpConfiguration = new MllpConfiguration();
        mllpConfiguration.setMaxConcurrentConsumers(maxConcurrentConsumers);
        MllpEndpoint mllpEndpoint = new MllpEndpoint("mllp://dummy", new MllpComponent(), mllpConfiguration);

        assertEquals(maxConcurrentConsumers, mllpEndpoint.getConfiguration().getMaxConcurrentConsumers());
    }

    /**
     * Assert that the idleTimeoutStrategy property overridden in the MllpConfiguration object is correctly set on the
     * endpoint instance.
     */
    @Test
    public void testCreateEndpointWithIdletimeoutStrategy() throws Exception {
        final MllpIdleTimeoutStrategy closeStrategy = MllpIdleTimeoutStrategy.CLOSE;
        final MllpIdleTimeoutStrategy resetStrategy = MllpIdleTimeoutStrategy.RESET;

        MllpComponent mllpComponent = context.getComponent("mllp", MllpComponent.class);

        MllpEndpoint defaultStrategyEndpoint
                = (MllpEndpoint) mllpComponent.createEndpoint("mllp://dummy:1234?idleTimeout=30000");
        assertEquals(resetStrategy, defaultStrategyEndpoint.getConfiguration().getIdleTimeoutStrategy());

        MllpEndpoint lowerCaseCloseStrategyEndpoint
                = (MllpEndpoint) mllpComponent.createEndpoint("mllp://dummy:1234?idleTimeout=30000&idleTimeoutStrategy=CLOSE");
        assertEquals(closeStrategy, lowerCaseCloseStrategyEndpoint.getConfiguration().getIdleTimeoutStrategy());

        MllpEndpoint upperCaseCloseStrategyEndpoint
                = (MllpEndpoint) mllpComponent.createEndpoint("mllp://dummy:1234?idleTimeout=30000&idleTimeoutStrategy=CLOSE");
        assertEquals(closeStrategy, upperCaseCloseStrategyEndpoint.getConfiguration().getIdleTimeoutStrategy());

        MllpEndpoint lowerCaseResetStrategyEndpoint
                = (MllpEndpoint) mllpComponent.createEndpoint("mllp://dummy:1234?idleTimeout=30000&idleTimeoutStrategy=reset");
        assertEquals(resetStrategy, lowerCaseResetStrategyEndpoint.getConfiguration().getIdleTimeoutStrategy());

        MllpEndpoint upperCaseResetStrategyEndpoint
                = (MllpEndpoint) mllpComponent.createEndpoint("mllp://dummy:1234?idleTimeout=30000&idleTimeoutStrategy=RESET");
        assertEquals(resetStrategy, upperCaseResetStrategyEndpoint.getConfiguration().getIdleTimeoutStrategy());
    }

}
