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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class MainFaultToleranceTest {

    @Test
    public void testMain() throws Exception {
        Main main = new Main();

        main.addInitialProperty("camel.faulttolerance.failure-ratio", "555");
        main.addInitialProperty("camel.faulttolerance.timeout-pool-size", "20");

        main.configure().faultTolerance()
                .withBulkheadEnabled(true)
                .withDelay(500L)
                .withSuccessThreshold(123)
                .withTimeoutPoolSize(5)
            .end();

        main.start();

        CamelContext context = main.getCamelContext();
        Assert.assertNotNull(context);

        ModelCamelContext mcc = context.adapt(ModelCamelContext.class);
        FaultToleranceConfigurationDefinition def = mcc.getFaultToleranceConfiguration(null);
        Assert.assertNotNull(def);

        Assert.assertEquals("500", def.getDelay());
        Assert.assertEquals("123", def.getSuccessThreshold());
        Assert.assertEquals("20", def.getTimeoutPoolSize());
        Assert.assertEquals("555", def.getFailureRatio());
        Assert.assertEquals("true", def.getBulkheadEnabled());

        main.stop();
    }

}
