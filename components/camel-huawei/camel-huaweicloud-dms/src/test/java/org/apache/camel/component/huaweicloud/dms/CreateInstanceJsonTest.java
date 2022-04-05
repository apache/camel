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
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceResponse;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreateInstanceJsonTest extends CamelTestSupport {
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

        String sampleBody = "{" +
                            "\"name\":\"" + testConfiguration.getProperty("name") + "\"," +
                            "\"description\":\"" + testConfiguration.getProperty("description") + "\"," +
                            "\"engine\":\"" + testConfiguration.getProperty("engine") + "\"," +
                            "\"engine_version\":\"" + testConfiguration.getProperty("engineVersion") + "\"," +
                            "\"specification\":\"" + testConfiguration.getProperty("specification") + "\"," +
                            "\"storage_space\":1000," +
                            "\"partition_num\":500," +
                            "\"access_user\":\"" + testConfiguration.getProperty("accessUser") + "\"," +
                            "\"password\":\"" + testConfiguration.getProperty("password") + "\"," +
                            "\"vpc_id\":\"" + testConfiguration.getProperty("vpcId") + "\"," +
                            "\"security_group_id\":\"" + testConfiguration.getProperty("securityGroupId") + "\"," +
                            "\"subnet_id\":\"" + testConfiguration.getProperty("subnetId") + "\"," +
                            "\"available_zones\":[\"" + testConfiguration.getProperty("availableZone") + "\"]," +
                            "\"product_id\":\"" + testConfiguration.getProperty("productId") + "\"," +
                            "\"kafka_manager_user\":\"" + testConfiguration.getProperty("kafkaManagerUser") + "\"," +
                            "\"kafka_manager_password\":\"" + testConfiguration.getProperty("kafkaManagerPassword") + "\"," +
                            "\"maintain_begin\":\"" + testConfiguration.getProperty("maintainBegin") + "\"," +
                            "\"maintain_end\":\"" + testConfiguration.getProperty("maintainEnd") + "\"," +
                            "\"enable_publicip\":true," +
                            "\"public_bandwidth\":\"" + testConfiguration.getProperty("public_bandwidth") + "\"," +
                            "\"publicip_id\":\"" + testConfiguration.getProperty("publicipId") + "\"," +
                            "\"ssl_enable\":true," +
                            "\"retention_policy\":\"" + testConfiguration.getProperty("retentionPolicy") + "\"," +
                            "\"connector_enable\":false," +
                            "\"enable_auto_topic\":true," +
                            "\"storage_spec_code\":\"" + testConfiguration.getProperty("storageSpecCode") + "\"," +
                            "\"enterprise_project_id\":\"" + testConfiguration.getProperty("enterpriseProjectId") + "\"" +
                            "}";
        template.sendBody("direct:operation", sampleBody);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertEquals("{\"instance_id\":\"test-instance-id\"}",
                responseExchange.getIn().getBody(String.class));
    }
}
