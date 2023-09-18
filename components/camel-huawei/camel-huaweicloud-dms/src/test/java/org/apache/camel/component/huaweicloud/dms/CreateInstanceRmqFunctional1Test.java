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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateInstanceRmqFunctional1Test extends CamelTestSupport {
    private static final String ACCESS_KEY = "replace_this_with_access_key";
    private static final String SECRET_KEY = "replace_this_with_secret_key";
    private static final String PROJECT_ID = "replace_this_with_project_id";
    private static final String REGION = "replace_this_with_region";

    // new RabbitMQ instance options: https://support.huaweicloud.com/en-us/api-rabbitmq/rabbitmq-api-180514002.html
    private static final String NAME = "replace_this_with_name";
    private static final String ENGINE_VERSION = "replace_this_with_engine_version";
    private static final String STORAGE_SPACE = "replace_this_with_storage_space";
    private static final String ACCESS_USER = "replace_this_with_storage_access_user";
    private static final String PASSWORD = "replace_this_with_password";
    private static final String VPC_ID = "replace_this_with_vpc_id";
    private static final String SECURITY_GROUP_ID = "replace_this_with_security_group_id";
    private static final String SUBNET_ID = "replace_this_with_subnet_id";
    private static final String AVAILABLE_ZONE = "replace_this_with_available_zone";
    private static final String PRODUCT_ID = "replace_this_with_product_id";
    private static final String STORAGE_SPEC_CODE = "replace_this_with_storage_spec_code";

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(ACCESS_KEY, SECRET_KEY);

    @BindToRegistry("availableZones")
    List<String> availableZones = new ArrayList<>();

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:operation")
                        .to("hwcloud-dms:createInstance?" +
                            "serviceKeys=#serviceKeys" +
                            "&projectId=" + PROJECT_ID +
                            "&region=" + REGION +
                            "&ignoreSslVerification=true" +
                            "&engine=rabbitmq" +

                            "&name=" + NAME +
                            "&engineVersion=" + ENGINE_VERSION +
                            "&storageSpace=" + STORAGE_SPACE +
                            "&accessUser=" + ACCESS_USER +
                            "&password=" + PASSWORD +
                            "&vpcId=" + VPC_ID +
                            "&securityGroupId=" + SECURITY_GROUP_ID +
                            "&subnetId=" + SUBNET_ID +
                            "&availableZones=#availableZones" +
                            "&productId=" + PRODUCT_ID +
                            "&storageSpecCode=" + STORAGE_SPEC_CODE)
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

        availableZones.add(AVAILABLE_ZONE);

        template.sendBody("direct:operation", null);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertNotNull(responseExchange.getIn().getBody(String.class));
        assertTrue(responseExchange.getIn().getBody(String.class).length() > 0);
    }
}
