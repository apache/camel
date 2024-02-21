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
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequestBody;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceResponse;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreateInstancePojoTest extends CamelTestSupport {
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
                        .to("hwcloud-dms:createInstance?" +
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
        CreateInstanceResponse response = new CreateInstanceResponse()
                .withInstanceId("test-instance-id");

        Mockito.when(mockClient.createInstance(Mockito.any(CreateInstanceRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:operation_result");
        mock.expectedMinimumMessageCount(1);

        List<String> availableZones = new ArrayList<>();
        availableZones.add(testConfiguration.getProperty("availableZone"));
        CreateInstanceRequestBody sampleBody = new CreateInstanceRequestBody()
                .withName(testConfiguration.getProperty("name"))
                .withDescription(testConfiguration.getProperty("description"))
                .withEngine(testConfiguration.getProperty("engine"))
                .withEngineVersion(testConfiguration.getProperty("engineVersion"))
                .withSpecification(testConfiguration.getProperty("specification"))
                .withStorageSpace(1000)
                .withPartitionNum(500)
                .withAccessUser(testConfiguration.getProperty("accessUser"))
                .withPassword(testConfiguration.getProperty("password"))
                .withVpcId(testConfiguration.getProperty("vpcId"))
                .withSecurityGroupId(testConfiguration.getProperty("securityGroupId"))
                .withSubnetId(testConfiguration.getProperty("subnetId"))
                .withAvailableZones(availableZones)
                .withProductId(testConfiguration.getProperty("productId"))
                .withKafkaManagerUser(testConfiguration.getProperty("kafkaManagerUser"))
                .withKafkaManagerPassword(testConfiguration.getProperty("kafkaManagerPassword"))
                .withMaintainBegin(testConfiguration.getProperty("maintainBegin"))
                .withMaintainEnd(testConfiguration.getProperty("maintainEnd"))
                .withEnablePublicip(true)
                .withPublicBandwidth(testConfiguration.getProperty("publicBandwidth"))
                .withPublicipId(testConfiguration.getProperty("publicipId"))
                .withSslEnable(true)
                .withRetentionPolicy(testConfiguration.getProperty("retentionPolicy"))
                .withConnectorEnable(false)
                .withEnableAutoTopic(true)
                .withStorageSpecCode(testConfiguration.getProperty("storageSpecCode"))
                .withEnterpriseProjectId(testConfiguration.getProperty("enterpriseProjectId"));
        template.sendBody("direct:operation", sampleBody);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals("{\"instance_id\":\"test-instance-id\"}",
                responseExchange.getIn().getBody(String.class));
    }
}
