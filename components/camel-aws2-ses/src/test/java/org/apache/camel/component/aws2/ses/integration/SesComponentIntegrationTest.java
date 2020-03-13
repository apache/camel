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
package org.apache.camel.component.aws2.ses.integration;

import java.util.Collections;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.ses.Ses2Constants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Must be manually tested. Provide your own accessKey and secretKey!")
public class SesComponentIntegrationTest extends CamelTestSupport {

    @Test
    public void sendUsingAccessKeyAndSecretKey() throws Exception {
        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Ses2Constants.SUBJECT, "This is my subject");
                exchange.getIn().setHeader(Ses2Constants.TO, Collections.singletonList("to@example.com"));
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertNotNull(exchange.getIn().getHeader(Ses2Constants.MESSAGE_ID));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("aws2-ses://from@example.com?accessKey=xxx&secretKey=yyy");
            }
        };
    }
}
