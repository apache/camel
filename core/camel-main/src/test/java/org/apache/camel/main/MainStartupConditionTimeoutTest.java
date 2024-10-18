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

import org.apache.camel.support.startup.EnvStartupCondition;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class MainStartupConditionTimeoutTest {

    @Test
    public void testCustomCondition() {
        StopWatch watch = new StopWatch();
        Main main = new Main();
        try {
            main.configure().startupCondition().withEnabled(true)
                    .withOnTimeout("fail")
                    .withTimeout(250)
                    .withCustomClassNames("org.apache.camel.main.MainStartupConditionTimeoutTest$MyEnvCondition");
            main.start();
            fail("Should throw exception");
        } catch (Exception e) {
            Assertions.assertEquals(
                    "Startup condition: ENV cannot continue due to: OS Environment Variable: MY_ENV does not exist",
                    e.getCause().getMessage());
        } finally {
            main.stop();
        }
        Assertions.assertTrue(watch.taken() < 3000);
    }

    public static class MyEnvCondition extends EnvStartupCondition {

        public MyEnvCondition() {
            super("MY_ENV");
        }

        @Override
        protected String lookupEnvironmentVariable(String env) {
            return null;
        }
    }
}
