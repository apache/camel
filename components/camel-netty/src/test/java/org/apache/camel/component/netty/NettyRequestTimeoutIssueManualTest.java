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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("This test can be run manually")
public class NettyRequestTimeoutIssueManualTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(NettyProducerHangTest.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:out")
                        .to("netty:tcp://localhost:8080?requestTimeout=5000");

                from("netty:tcp://localhost:8080")
                        .to("log:nettyCase?showAll=true&multiline=true");
            }
        };
    }

    @Test
    public void test() throws Exception {
        String result = template.requestBody("direct:out", "hello", String.class);
        assertEquals("hello", result);

        LOG.info("Sleeping for 20 seconds, and no Netty exception should occur");
        Thread.sleep(20000);
    }
}
