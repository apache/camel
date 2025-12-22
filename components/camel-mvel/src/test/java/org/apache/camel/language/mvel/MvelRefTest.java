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
package org.apache.camel.language.mvel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MvelRefTest extends CamelTestSupport {

    private static final String TEMP = "@{\"The result is \" + body * headers.multiplier}";

    @Test
    public void testRef() {
        Exchange exchange = template.request("direct:a", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(7);
                exchange.getIn().setHeader("multiplier", 3);
            }
        });

        assertEquals("The result is 21", exchange.getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.getRegistry().bind("mytemp", TEMP);

                from("direct:a").to(
                        "mvel:ref:mytemp");
            }
        };
    }
}
