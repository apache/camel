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
package org.apache.camel.component.google.secret.manager.unit;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.secret.manager.GoogleSecretManagerPropertiesFunction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleSecretManagerPropertiesFunctionTest {

    private GoogleSecretManagerPropertiesFunction functionReturning(String secret) {
        SecretManagerServiceClient client = mock(SecretManagerServiceClient.class);
        when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(secret))).build());
        return new GoogleSecretManagerPropertiesFunction(client, "project");
    }

    @Test
    void aField() {
        assertThat(functionReturning("{\"username\":\"camel\"}").apply("test#username")).isEqualTo("camel");
    }

    @Test
    void contentAfterTheFirstJsonValueIsIgnored() {
        assertThat(functionReturning("{\"username\":\"camel\"} trailing").apply("test#username")).isEqualTo("camel");
    }

    @Test
    void aFieldOfASecretThatIsNotJsonFailsEvenWithADefault() {
        GoogleSecretManagerPropertiesFunction function = functionReturning("not-json");
        assertThatThrownBy(() -> function.apply("test#username")).isInstanceOf(RuntimeCamelException.class);
        assertThatThrownBy(() -> function.apply("test#username:fallback")).isInstanceOf(RuntimeCamelException.class);
    }

    @Test
    void aFieldThatIsNotAStringGivesTheDefault() {
        assertThat(functionReturning("{\"port\":5432}").apply("test#port:fallback")).isEqualTo("fallback");
    }
}
