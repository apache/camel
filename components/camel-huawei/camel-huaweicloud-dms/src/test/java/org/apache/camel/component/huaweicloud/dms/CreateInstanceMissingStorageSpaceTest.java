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
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "Storage space is mandatory" guard must fire when storageSpace is left at its endpoint default. It comes from a
 * primitive int option, so the previous {@code ObjectHelper.isEmpty(Integer)} check was always false and the guard
 * never triggered (storage_space:0 was sent instead).
 */
public class CreateInstanceMissingStorageSpaceTest extends CamelTestSupport {
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
                // storageSpace intentionally omitted -> endpoint default 0
                            "&accessUser=" + testConfiguration.getProperty("accessUser") +
                            "&password=" + testConfiguration.getProperty("password") +
                            "&vpcId=" + testConfiguration.getProperty("vpcId") +
                            "&securityGroupId=" + testConfiguration.getProperty("securityGroupId") +
                            "&subnetId=" + testConfiguration.getProperty("subnetId") +
                            "&availableZones=#availableZones" +
                            "&productId=" + testConfiguration.getProperty("productId") +
                            "&storageSpecCode=" + testConfiguration.getProperty("storageSpecCode"))
                        .to("mock:operation_result");
            }
        };
    }

    @Test
    public void missingStorageSpaceIsRejected() {
        availableZones.add(testConfiguration.getProperty("availableZone"));

        Exchange result = template.request("direct:operation", e -> e.getIn().setBody("sample_body"));

        Throwable cause = result.getException();
        assertNotNull(cause, "a create with no storageSpace was expected to fail");
        boolean mandatory = false;
        while (cause != null) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null
                    && cause.getMessage().contains("Storage space is mandatory")) {
                mandatory = true;
                break;
            }
            cause = cause.getCause();
        }
        assertTrue(mandatory, "expected 'Storage space is mandatory', got: " + result.getException());
    }
}
