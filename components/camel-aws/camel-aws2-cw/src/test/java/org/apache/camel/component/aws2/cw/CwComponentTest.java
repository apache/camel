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
package org.apache.camel.component.aws2.cw;

import java.time.Instant;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

public class CwComponentTest extends CamelTestSupport {

    @BindToRegistry("now")
    public static final Instant NOW = Instant.now();

    public static final Instant LATER = Instant.ofEpochMilli(NOW.getNano() + 1);

    @BindToRegistry("amazonCwClient")
    private CloudWatchClient cloudWatchClient = new CloudWatchClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void sendMetricFromHeaderValues() throws Exception {
        mock.expectedMessageCount(1);
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Cw2Constants.METRIC_NAMESPACE, "camel.apache.org/overriden");
                exchange.getIn().setHeader(Cw2Constants.METRIC_NAME, "OverridenMetric");
                exchange.getIn().setHeader(Cw2Constants.METRIC_VALUE, Double.valueOf(3));
                exchange.getIn().setHeader(Cw2Constants.METRIC_UNIT, StandardUnit.BYTES.toString());
                exchange.getIn().setHeader(Cw2Constants.METRIC_TIMESTAMP, LATER);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(
                        "aws2-cw://camel.apache.org/test?amazonCwClient=#amazonCwClient&name=testMetric&unit=BYTES&timestamp=#now")
                        .to("mock:result");
            }
        };
    }
}
