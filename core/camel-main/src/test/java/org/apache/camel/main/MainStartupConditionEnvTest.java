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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.support.startup.EnvStartupCondition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainStartupConditionEnvTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Test
    public void testCustomCondition() {
        Main main = new Main();
        try {
            main.configure().startupCondition().withEnabled(true)
                    .withCustomClassNames("org.apache.camel.main.MainStartupConditionEnvTest$MyEnvCondition");
            main.start();

            Assertions.assertEquals(3, COUNTER.get());
        } finally {
            main.stop();
        }
    }

    public static class MyEnvCondition extends EnvStartupCondition {

        public MyEnvCondition() {
            super("MY_ENV");
        }

        @Override
        protected String lookupEnvironmentVariable(String env) {
            if (COUNTER.incrementAndGet() < 3) {
                return null;
            }
            return "FOO";
        }
    }
}
