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
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UpdateGroupJsonTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateGroupJsonTest.class.getName());

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("iamClient")
    IAMMockClient mockClient = new IAMMockClient(null);

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(
            testConfiguration.getProperty("accessKey"),
            testConfiguration.getProperty("secretKey"));

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:update_group")
                        .setProperty("CamelHwCloudIamGroupId", constant(testConfiguration.getProperty("groupId")))
                        .to("hwcloud-iam:updateGroup?" +
                            "region=" + testConfiguration.getProperty("region") +
                            "&ignoreSslVerification=true" +
                            "&iamClient=#iamClient" +
                            "&serviceKeys=#serviceKeys")
                        .log("update group successful")
                        .to("mock:update_group_result");
            }
        };
    }

    @Test
    public void testUpdateGroup() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:update_group_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:update_group", "{\"name\":\"Group 43\",\"description\":\"Group description\"}");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals("{\"description\":\"Group description\",\"domainId\":\"123\",\"name\":\"Group 43\"}",
                responseExchange.getIn().getBody(String.class));
    }
}
