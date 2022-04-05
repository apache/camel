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
package org.apache.camel.component.huaweicloud.iam;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ListGroupsTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ListGroupsTest.class.getName());

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("iamClient")
    IAMMockClient mockClient = new IAMMockClient(null);

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list_groups")
                        .to("hwcloud-iam:listGroups?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&iamClient=#iamClient")
                        .log("List groups successful")
                        .to("mock:list_groups_result");
            }
        };
    }

    @Test
    public void testListGroups() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:list_groups_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:list_groups", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals("[{\"description\":\"First group\",\"id\":\"group1_id\",\"name\":\"Group 1\"}," +
                     "{\"description\":\"Second group\",\"id\":\"group2_id\",\"name\":\"Group 2\"}]",
                responseExchange.getIn().getBody(String.class));
    }
}
