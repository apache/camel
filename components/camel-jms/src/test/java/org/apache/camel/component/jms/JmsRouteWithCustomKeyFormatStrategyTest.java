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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StringHelper;

/**
 * With the passthrough option
 */
public class JmsRouteWithCustomKeyFormatStrategyTest extends JmsRouteWithDefaultKeyFormatStrategyTest {

    @BindToRegistry("myJmsKeyStrategy")
    private MyCustomKeyFormatStrategy strategy = new MyCustomKeyFormatStrategy();

    @Override
    protected String getUri() {
        return "activemq:queue:foo?jmsKeyFormatStrategy=#myJmsKeyStrategy";
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(getUri());

                from(getUri()).to("mock:result");
            }
        };
    }

    private static class MyCustomKeyFormatStrategy implements JmsKeyFormatStrategy {

        @Override
        public String encodeKey(String key) {
            return "FOO" + key + "BAR";
        }

        @Override
        public String decodeKey(String key) {
            return StringHelper.between(key, "FOO", "BAR");
        }
    }
}
