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
package org.apache.camel.component.huaweicloud.dms;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.dms.constants.DMSProperties;
import org.apache.camel.component.huaweicloud.dms.models.DmsInstance;
import org.apache.camel.component.huaweicloud.dms.models.QueryInstanceRequest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueryInstanceTest extends CamelTestSupport {
    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("dmsClient")
    DmsClient mockClient = Mockito.mock(DmsClient.class);

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(
            testConfiguration.getProperty("accessKey"),
            testConfiguration.getProperty("secretKey"));

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:operation")
                        .setProperty(DMSProperties.OPERATION, constant("queryInstance"))
                        .to("hwcloud-dms:?" +
                            "serviceKeys=#serviceKeys" +
                            "&projectId=" + testConfiguration.getProperty("projectId") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&instanceId=" + testConfiguration.getProperty("instanceId") +
                            "&ignoreSslVerification=true" +
                            "&dmsClient=#dmsClient")
                        .log("Operation successful")
                        .to("mock:operation_result");
            }
        };
    }

    @Test
    public void testOperation() throws Exception {
        DmsInstance instance = new DmsInstance()
                .withName("test-instance-1")
                .withEngine(testConfiguration.getProperty("engine"))
                .withStorageSpace(500)
                .withInstanceId("id-1")
                .withVpcId("vpc-id-1")
                .withUserName("user-1");

        Mockito.when(mockClient.queryInstance(Mockito.any(QueryInstanceRequest.class))).thenReturn(instance);

        MockEndpoint mock = getMockEndpoint("mock:operation_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:operation", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals(
                "{\"name\":\"test-instance-1\",\"engine\":\"kafka\",\"storage_space\":500,\"partition_num\":0,\"used_storage_space\":0,\"port\":0,"
                     +
                     "\"instance_id\":\"id-1\",\"charging_mode\":0,\"vpc_id\":\"vpc-id-1\",\"user_name\":\"user-1\",\"enable_publicip\":false,\"ssl_enable\":false,"
                     +
                     "\"is_logical_volume\":false,\"extend_times\":0,\"enable_auto_topic\":false}",
                responseExchange.getIn().getBody(String.class));
    }
}
