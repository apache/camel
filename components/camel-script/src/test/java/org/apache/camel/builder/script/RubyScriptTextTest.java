/**
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
package org.apache.camel.builder.script;


import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

public class RubyScriptTextTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    MockEndpoint resultEndpoint;

    final int messageCount = 500;

    @Test
    public void parallelExecutionWithCachedScriptAndReusedScriptEngine() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                getContext().getGlobalOptions().put(Exchange.REUSE_SCRIPT_ENGINE, "true");
                //getContext().getProperties().put(Exchange.COMPILE_SCRIPT, "true");

                from("seda:jruby?concurrentConsumers=5")
                    .to("language:ruby:puts $request.body;result = $request.body?cacheScript=true")
                    .to(resultEndpoint);
            }
        });

        resultEndpoint.setExpectedMessageCount(messageCount);
        resultEndpoint.assertNoDuplicates(body());

        for (int i = 1; i < messageCount + 1; i++) {
            template.sendBody("seda:jruby", "BODY" + i);
        }

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    @Ignore
    public void parallelExecutionWithoutCachedScriptAndNewScriptEngineForEachExchange() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                //getContext().getProperties().put(Exchange.COMPILE_SCRIPT, "true");

                from("seda:jruby?concurrentConsumers=5")
                    .to("language:ruby:puts $request.body;result = $request.body")
                    .to(resultEndpoint);
            }
        });

        resultEndpoint.setExpectedMessageCount(messageCount);
        resultEndpoint.assertNoDuplicates(body());

        for (int i = 1; i < messageCount + 1; i++) {
            template.sendBody("seda:jruby", "BODY" + i);
        }

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }
}
