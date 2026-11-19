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
package org.apache.camel.component.openai;

import java.lang.reflect.Proxy;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.opentelemetry.instrumentation.openai.v1_1.InstrumentedOpenAiClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIClientTracingTest {

    @Test
    void shouldDetectAClientWrappedByOpenTelemetry() {
        Object wrapped = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { OpenAIClient.class },
                new InstrumentedOpenAiClient());

        assertThat(OpenAIClientTracing.isTraced(wrapped)).isTrue();
    }

    @Test
    void shouldNotDetectAPlainClientOrAnotherProxy() {
        OpenAIClient plain = OpenAIOkHttpClient.builder().apiKey("dummy").baseUrl("http://localhost:1").build();
        Object otherProxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { OpenAIClient.class },
                (proxy, method, args) -> null);

        assertThat(OpenAIClientTracing.isTraced(plain)).isFalse();
        assertThat(OpenAIClientTracing.isTraced(otherProxy)).isFalse();
        assertThat(OpenAIClientTracing.isTraced(null)).isFalse();
    }
}
