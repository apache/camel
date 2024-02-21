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

public class GetGroupUsersTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GetGroupUsersTest.class.getName());

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("iamClient")
    IAMMockClient mockClient = new IAMMockClient(null);

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:get_group_users")
                        .setProperty("CamelHwCloudIamOperation", constant("getGroupUsers"))
                        .setProperty("CamelHwCloudIamGroupId", constant(testConfiguration.getProperty("groupId")))
                        .to("hwcloud-iam:dummy?" +
                            "region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&iamClient=#iamClient")
                        .log("Get user successful")
                        .to("mock:get_group_users_result");
            }
        };
    }

    @Test
    public void testGetGroupUsers() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:get_group_users_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:get_group_users", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals(
                "[{\"name\":\"User 9\",\"id\":\"abc\"},{\"name\":\"User 10\",\"description\":\"Employee\",\"id\":\"def\"}]",
                responseExchange.getIn().getBody(String.class));
    }
}
