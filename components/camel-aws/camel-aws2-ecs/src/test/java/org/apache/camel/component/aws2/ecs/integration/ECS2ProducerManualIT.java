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
package org.apache.camel.component.aws2.ecs.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class ECS2ProducerManualIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void translateTextTest() {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listClusters", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Test");
            }
        });

        ListClustersResponse resultGet = (ListClustersResponse) exchange.getIn().getBody();
        assertEquals(0, resultGet.clusterArns().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listClusters")
                        .to("aws2-ecs://test?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=eu-west-1&operation=listClusters")
                        .to("mock:result");
            }
        };
    }
}
