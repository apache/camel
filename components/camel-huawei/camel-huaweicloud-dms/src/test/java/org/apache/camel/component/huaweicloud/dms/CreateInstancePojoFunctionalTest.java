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
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequestBody;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateInstancePojoFunctionalTest extends CamelTestSupport {
    private static final String ACCESS_KEY = "replace_this_with_access_key";
    private static final String SECRET_KEY = "replace_this_with_secret_key";
    private static final String PROJECT_ID = "replace_this_with_project_id";
    private static final String REGION = "replace_this_with_region";

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(ACCESS_KEY, SECRET_KEY);

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:operation")
                        .to("hwcloud-dms:createInstance?" +
                            "serviceKeys=#serviceKeys" +
                            "&projectId=" + PROJECT_ID +
                            "&region=" + REGION +
                            "&ignoreSslVerification=true")
                        .log("Operation successful")
                        .to("log:LOG?showAll=true")
                        .to("mock:operation_result");
            }
        };
    }

    /**
     * The following test cases should be manually enabled to perform test against the actual HuaweiCloud DMS server
     * with real user credentials. To perform this test, manually comment out the @Ignore annotation and enter relevant
     * service parameters in the placeholders above (static variables of this test class)
     *
     * @throws Exception
     */
    @Disabled("Manually enable this once you configure the parameters in the placeholders above")
    @Test
    public void testOperation() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:operation_result");
        mock.expectedMinimumMessageCount(1);

        // new Kafka instance options: https://support.huaweicloud.com/en-us/api-kafka/kafka-api-180514002.html
        // new RabbitMQ instance options: https://support.huaweicloud.com/en-us/api-rabbitmq/rabbitmq-api-180514002.html
        List<String> availableZones = new ArrayList<>();
        availableZones.add("replace_with_instance_information");
        CreateInstanceRequestBody sampleBody = new CreateInstanceRequestBody()
                .withName("replace_with_instance_information")
                .withDescription("replace_with_instance_information")
                .withEngine("replace_with_instance_information")
                .withEngineVersion("replace_with_instance_information")
                .withSpecification("replace_with_instance_information")
                .withStorageSpace(0/*replace_with_instance_information*/)
                .withPartitionNum(0/*replace_with_instance_information*/)
                .withAccessUser("replace_with_instance_information")
                .withPassword("replace_with_instance_information")
                .withVpcId("replace_with_instance_information")
                .withSecurityGroupId("replace_with_instance_information")
                .withSubnetId("replace_with_instance_information")
                .withAvailableZones(availableZones)
                .withProductId("replace_with_instance_information")
                .withKafkaManagerUser("replace_with_instance_information")
                .withKafkaManagerPassword("replace_with_instance_information")
                .withMaintainBegin("replace_with_instance_information")
                .withMaintainEnd("replace_with_instance_information")
                .withEnablePublicip(false/*replace_with_instance_information*/)
                .withPublicBandwidth("replace_with_instance_information")
                .withPublicipId("replace_with_instance_information")
                .withSslEnable(false/*replace_with_instance_information*/)
                .withRetentionPolicy("replace_with_instance_information")
                .withConnectorEnable(false/*replace_with_instance_information*/)
                .withEnableAutoTopic(false/*replace_with_instance_information*/)
                .withStorageSpecCode("replace_with_instance_information")
                .withEnterpriseProjectId("replace_with_instance_information");

        template.sendBody("direct:operation", sampleBody);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertNotNull(responseExchange.getIn().getBody(String.class));
        assertTrue(responseExchange.getIn().getBody(String.class).length() > 0);
    }
}
