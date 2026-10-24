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
package org.apache.camel.component.aws2.stepfunctions;

import org.apache.camel.component.aws2.stepfunctions.client.StepFunctions2ClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.services.sfn.SfnClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StepFunctions2ClientFactoryTest {

    @Test
    public void hostPrefixInjectionDisabledWithEndpointOverride() {
        StepFunctions2Configuration configuration = new StepFunctions2Configuration();
        configuration.setAccessKey("xxx");
        configuration.setSecretKey("yyy");
        configuration.setRegion("eu-west-1");
        configuration.setOverrideEndpoint(true);
        configuration.setUriEndpointOverride("http://localhost:4566");

        try (SfnClient client = StepFunctions2ClientFactory.getSfnClient(configuration)) {
            ClientOverrideConfiguration override = client.serviceClientConfiguration().overrideConfiguration();
            assertTrue(override.advancedOption(SdkAdvancedClientOption.DISABLE_HOST_PREFIX_INJECTION).orElse(false));
        }
    }

    @Test
    public void hostPrefixInjectionEnabledWithoutEndpointOverride() {
        StepFunctions2Configuration configuration = new StepFunctions2Configuration();
        configuration.setAccessKey("xxx");
        configuration.setSecretKey("yyy");
        configuration.setRegion("eu-west-1");

        try (SfnClient client = StepFunctions2ClientFactory.getSfnClient(configuration)) {
            ClientOverrideConfiguration override = client.serviceClientConfiguration().overrideConfiguration();
            assertFalse(override.advancedOption(SdkAdvancedClientOption.DISABLE_HOST_PREFIX_INJECTION).orElse(false));
        }
    }
}
