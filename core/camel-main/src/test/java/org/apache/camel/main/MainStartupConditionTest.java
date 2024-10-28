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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.StartupCondition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class MainStartupConditionTest {

    @Test
    public void testCustomCondition() {
        Main main = new Main();
        try {
            main.configure().withRoutesBuilderClasses("org.apache.camel.main.MainStartupConditionTest$MyRoute");
            main.configure().startupCondition().withEnabled(true).withTimeout(250).withInterval(100)
                    .withCustomClassNames("org.apache.camel.main.MainStartupConditionTest$MyCondition");
            main.start();
            fail("Should throw exception");
        } catch (Exception e) {
            Assertions.assertEquals("Startup condition timeout error", e.getCause().getMessage());
        } finally {
            main.stop();
        }
    }

    public static class MyCondition implements StartupCondition {

        @Override
        public boolean canContinue(CamelContext camelContext) throws Exception {
            return false;
        }
    }

    public static class MyRoute extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:start")
                    .to("mock:result");
        }
    }

}
