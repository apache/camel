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
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceResponse;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreateInstanceRmq1Test extends CamelTestSupport {
    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("dmsClient")
    DmsClient mockClient = Mockito.mock(DmsClient.class);

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(
            testConfiguration.getProperty("accessKey"),
            testConfiguration.getProperty("secretKey"));

    @BindToRegistry("availableZones")
    List<String> availableZones = new ArrayList<>();

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:operation")
                        .to("hwcloud-dms:createInstance?" +
                            "serviceKeys=#serviceKeys" +
                            "&projectId=" + testConfiguration.getProperty("projectId") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&instanceId=" + testConfiguration.getProperty("instanceId") +
                            "&ignoreSslVerification=true" +
                            "&dmsClient=#dmsClient" +

                            "&name=" + testConfiguration.getProperty("name") +
                            "&engine=rabbitmq" +
                            "&engineVersion=" + testConfiguration.getProperty("engineVersion") +
                            "&storageSpace=1000" +
                            "&accessUser=" + testConfiguration.getProperty("accessUser") +
                            "&password=" + testConfiguration.getProperty("password") +
                            "&vpcId=" + testConfiguration.getProperty("vpcId") +
                            "&securityGroupId=" + testConfiguration.getProperty("securityGroupId") +
                            "&subnetId=" + testConfiguration.getProperty("subnetId") +
                            "&availableZones=#availableZones" +
                            "&productId=" + testConfiguration.getProperty("productId") +
                            "&storageSpecCode=" + testConfiguration.getProperty("storageSpecCode"))
                        .log("Operation successful")
                        .to("mock:operation_result");
            }
        };
    }

    @Test
    public void testOperation() throws Exception {
        CreateInstanceResponse response = new CreateInstanceResponse()
                .withInstanceId("test-instance-id");

        Mockito.when(mockClient.createInstance(Mockito.any(CreateInstanceRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:operation_result");
        mock.expectedMinimumMessageCount(1);

        availableZones.add(testConfiguration.getProperty("availableZone"));

        template.sendBody("direct:operation", "sample_body");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals("{\"instance_id\":\"test-instance-id\"}",
                responseExchange.getIn().getBody(String.class));
    }
}
