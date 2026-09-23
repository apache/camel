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
package org.apache.camel.component.typesafeai;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class TypeSafeAiTimeoutTest {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpTimeoutsHaveTheSamePublicTypeAsTheOverallDeadline(boolean connecting) throws Exception {
        HttpTimeoutException cause = connecting
                ? new HttpConnectTimeoutException("connect timeout")
                : new HttpTimeoutException("request timeout");
        HttpClient http = mock(HttpClient.class);
        HttpClient.Builder builder = mock(HttpClient.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(http);
        when(http.sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>> any()))
                .thenReturn(CompletableFuture.failedFuture(cause));
        TypeSafeAiConfiguration configuration = new TypeSafeAiConfiguration();
        configuration.setApiKey("test-key");
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newBuilder).thenReturn(builder);
            try (TypeSafeAiClient client = new TypeSafeAiClient(configuration)) {
                assertThatThrownBy(() -> client.evaluate(TypeSafeAiTestSupport.request("Refund")))
                        .isExactlyInstanceOf(TimeoutException.class).hasCause(cause);
            }
        }
    }
}
