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
package org.apache.camel.component.smpp.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Spring based integration test for the smpp component. To run this test, ensure that the SMSC is running on: host:
 * localhost port: 2775 user: smppclient password: password <br/>
 * In the past, a SMSC for test was available here: http://www.seleniumsoftware.com/downloads.html.
 *
 * Since it is not available anymore, it's possible to test the reconnect logic manually using the nc CLI tool:
 *
 * nc -lv 2775
 */
@Disabled("Must be manually tested")
public class SmppConsumerReconnectManualIT extends CamelTestSupport {

    @Test
    public void test() throws Exception {
        Thread.sleep(1000000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("smpp://smppclient@localhost:2775?password=password&enquireLinkTimer=3000&transactionTimer=5000&systemType=consumer")
                        .to("mock:result");
            }
        };
    }
}
