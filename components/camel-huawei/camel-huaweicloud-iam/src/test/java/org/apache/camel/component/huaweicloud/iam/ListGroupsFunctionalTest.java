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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ListGroupsFunctionalTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ListGroupsFunctionalTest.class.getName());

    private static final String ACCESS_KEY = "replace_this_with_access_key";
    private static final String SECRET_KEY = "replace_this_with_secret_key";
    private static final String REGION = "replace_this_with_region";

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:list_groups")
                        .to("hwcloud-iam:listGroups?" +
                            "accessKey=" + ACCESS_KEY +
                            "&secretKey=" + SECRET_KEY +
                            "&region=" + REGION +
                            "&ignoreSslVerification=true")
                        .log("List groups successful")
                        .to("log:LOG?showAll=true")
                        .to("mock:list_groups_result");
            }
        };
    }

    /**
     * The following test cases should be manually enabled to perform test against the actual HuaweiCloud IAM server
     * with real user credentials. To perform this test, manually comment out the @Ignore annotation and enter relevant
     * service parameters in the placeholders above (static variables of this test class)
     *
     * @throws Exception
     */
    @Disabled("Manually enable this once you configure the parameters in the placeholders above")
    @Test
    public void testListGroups() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:list_groups_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:list_groups", "sampleBody");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertNotNull(responseExchange.getIn().getBody(String.class));
        assertTrue(responseExchange.getIn().getBody(String.class).length() > 0);
    }
}
