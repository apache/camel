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
package org.apache.camel.component.jms;

import org.apache.camel.BindToRegistry;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * With the pass-through option
 */
@Isolated("Shares the context with the parent class")
public class JmsRouteWithCustomKeyFormatStrategyTest extends JmsRouteWithDefaultKeyFormatStrategyTest {

    @BindToRegistry("myJmsKeyStrategy")
    private final MyCustomKeyFormatStrategy strategy = new MyCustomKeyFormatStrategy();

    @Override
    protected String getUri() {
        return "activemq:queue:JmsRouteWithCustomKeyFormatStrategyTest?jmsKeyFormatStrategy=#myJmsKeyStrategy";
    }

    private static class MyCustomKeyFormatStrategy implements JmsKeyFormatStrategy {

        @Override
        public String encodeKey(String key) {
            key = key.replace("-", "_HYPHEN_")
                    .replace(".", "_DOT_");

            return "FOO" + key + "BAR";
        }

        @Override
        public String decodeKey(String key) {
            if (key.startsWith("FOO") && key.endsWith("BAR")) {
                key = key.replace("_HYPHEN_", "-")
                        .replace("_DOT_", ".");

                return StringHelper.between(key, "FOO", "BAR");
            } else {
                return key;
            }
        }
    }
}
