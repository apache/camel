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
package org.apache.camel.issues;

import java.util.Map;
import java.util.UUID;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("CAMEL-8086: used for manual testing a memory issue")
public class DynamicRouterConvertBodyToIssueTest extends ContextTestSupport implements Processor {

    private static final int MAX_ITERATIONS = 1000;
    private static int counter;

    @Test
    public void testIssue() throws Exception {
        template.sendBody("seda:foo", "Hello World");

        Thread.sleep(60000);
    }

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo")
                    .dynamicRouter().method(DynamicRouterConvertBodyToIssueTest.class, "slip")
                    .to("mock:result");

                from("direct:while_body")
                    .process(new DynamicRouterConvertBodyToIssueTest())
                    .convertBodyTo(String.class);
            }
        };
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        log.info("Some: " + counter);

        exchange.setProperty("EXIT", "NO");
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 10000; i++) {
            sb.append(UUID.randomUUID().toString());
        }
        exchange.getIn().setBody(sb);

        Thread.sleep(100);

        if (counter++ > MAX_ITERATIONS) {
            exchange.setProperty("EXIT", "PLEASE");
        }
    }

    public String slip(String body, @ExchangeProperties Map<String, Object> properties) {
        log.info("slip " + properties.get("EXIT"));
        if (properties.get("EXIT") != null && properties.get("EXIT").equals("PLEASE")) {
            log.info("Exiting after " + MAX_ITERATIONS + " iterations");
            return null;
        } else {
            return "direct:while_body";
        }
    }

}
