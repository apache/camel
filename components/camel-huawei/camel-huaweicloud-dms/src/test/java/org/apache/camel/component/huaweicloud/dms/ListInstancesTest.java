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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.dms.models.DmsInstance;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesRequest;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesResponse;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ListInstancesTest extends CamelTestSupport {
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
                        .to("hwcloud-dms:listInstances?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&projectId=" + testConfiguration.getProperty("projectId") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&engine=" + testConfiguration.getProperty("engine") +
                            "&ignoreSslVerification=true" +
                            "&dmsClient=#dmsClient")
                        .log("Operation successful")
                        .to("mock:operation_result");
            }
        };
    }

    @Test
    public void testOperation() throws Exception {
        List<DmsInstance> instances = new ArrayList<>();
        DmsInstance instance1 = new DmsInstance()
                .withName("test-instance-1")
                .withEngine(testConfiguration.getProperty("engine"))
                .withStorageSpace(500)
                .withInstanceId("id-1")
                .withVpcId("vpc-id-1")
                .withUserName("user-1");
        DmsInstance instance2 = new DmsInstance()
                .withName("test-instance-2")
                .withEngine(testConfiguration.getProperty("engine"))
                .withStorageSpace(4932)
                .withInstanceId("id-2")
                .withVpcId("vpc-id-2")
                .withUserName("user-2")
                .withLogicalVolume(true);
        instances.add(instance1);
        instances.add(instance2);
        ListInstancesResponse response = new ListInstancesResponse()
                .withInstances(instances)
                .withInstanceNum(2);
        Mockito.when(mockClient.listInstances(Mockito.any(ListInstancesRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:operation_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:operation", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals(
                "[{\"name\":\"test-instance-1\",\"engine\":\"kafka\",\"storage_space\":500,\"partition_num\":0,\"used_storage_space\":0,\"port\":0,"
                     +
                     "\"instance_id\":\"id-1\",\"charging_mode\":0,\"vpc_id\":\"vpc-id-1\",\"user_name\":\"user-1\",\"enable_publicip\":false,\"ssl_enable\":false,"
                     +
                     "\"is_logical_volume\":false,\"extend_times\":0,\"enable_auto_topic\":false},{\"name\":\"test-instance-2\",\"engine\":\"kafka\","
                     +
                     "\"storage_space\":4932,\"partition_num\":0,\"used_storage_space\":0,\"port\":0,\"instance_id\":\"id-2\",\"charging_mode\":0,\"vpc_id\":\"vpc-id-2\","
                     +
                     "\"user_name\":\"user-2\",\"enable_publicip\":false,\"ssl_enable\":false,\"is_logical_volume\":true,\"extend_times\":0,\"enable_auto_topic\":false}]",
                responseExchange.getIn().getBody(String.class));
    }
}
