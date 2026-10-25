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
package org.apache.camel.component.aws.secretsmanager;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecretsManagerPropertiesFunctionTest {

    private final SecretsManagerClient client = mock(SecretsManagerClient.class);

    @Test
    public void binarySecretIsReadAsTheTextOfItsBytes() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(GetSecretValueResponse.builder()
                .secretBinary(SdkBytes.fromUtf8String("test-value-4")).build());

        SecretsManagerPropertiesFunction function = new SecretsManagerPropertiesFunction(client);
        assertEquals("test-value-4", function.apply("test"));
    }

    @Test
    public void binarySecretWithAField() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(GetSecretValueResponse.builder()
                .secretBinary(SdkBytes.fromUtf8String("{\"username\":\"camel\"}")).build());

        SecretsManagerPropertiesFunction function = new SecretsManagerPropertiesFunction(client);
        assertEquals("camel", function.apply("test#username"));
    }

    @Test
    public void stringSecret() {
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(GetSecretValueResponse.builder()
                .secretString("value").build());

        SecretsManagerPropertiesFunction function = new SecretsManagerPropertiesFunction(client);
        assertEquals("value", function.apply("test"));
    }
}
