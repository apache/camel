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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.StartupCondition;
import org.apache.camel.spi.StartupConditionStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class StartupConditionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testVetoCamelContextStart() {
        try {
            context.start();
            fail("Should throw exception");
        } catch (Exception e) {
            assertEquals("Startup condition: MyCondition cannot continue due to: forced error from unit test",
                    e.getCause().getMessage());
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        StartupConditionStrategy scs = context.getCamelContextExtension().getContextPlugin(StartupConditionStrategy.class);
        scs.setEnabled(true);
        scs.setTimeout(250);
        scs.setOnTimeout("fail");
        scs.addStartupCondition(new MyCondition());
        return context;
    }

    private static class MyCondition implements StartupCondition {

        @Override
        public String getFailureMessage() {
            return "forced error from unit test";
        }

        @Override
        public boolean canContinue(CamelContext camelContext) throws Exception {
            return false;
        }
    }
}
