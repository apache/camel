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

import com.openai.core.http.Headers;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import org.apache.camel.component.ai.observability.GenAiErrorCategory;
import org.apache.camel.component.ai.observability.GenAiErrorProperties;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIErrorMetadataTest extends CamelTestSupport {

    @Test
    void shouldClassifyOpenAiRateLimitException() {
        RateLimitException error = RateLimitException.builder()
                .headers(Headers.builder().put("Retry-After-Ms", "1500").build())
                .build();

        assertThat(GenAiErrorSupport.classify(error)).isEqualTo(GenAiErrorCategory.RATE_LIMIT);
        assertThat(GenAiErrorSupport.extractRetryAfterMillis(error)).isEqualTo(1500L);
    }

    @Test
    void shouldParseRetryAfterSecondsHeader() {
        RateLimitException error = RateLimitException.builder()
                .headers(Headers.builder().put("Retry-After", "12").build())
                .build();

        assertThat(GenAiErrorSupport.extractRetryAfterMillis(error)).isEqualTo(12_000L);
    }

    @Test
    void shouldClassifyOpenAiUnauthorizedException() {
        UnauthorizedException error = UnauthorizedException.builder()
                .headers(Headers.builder().build())
                .build();
        assertThat(GenAiErrorSupport.classify(error)).isEqualTo(GenAiErrorCategory.AUTH);
        assertThat(GenAiErrorSupport.extractRetryAfterMillis(error)).isNull();
    }

    @Test
    void shouldApplyOpenAiErrorPropertiesToExchange() {
        RateLimitException error = RateLimitException.builder()
                .headers(Headers.builder().put("Retry-After", "5").build())
                .build();
        DefaultExchange exchange = new DefaultExchange(context);

        GenAiErrorSupport.apply(exchange, error);

        assertThat(exchange.getProperty(GenAiErrorProperties.ERROR_CATEGORY, String.class))
                .isEqualTo(GenAiErrorCategory.RATE_LIMIT.name());
        assertThat(exchange.getProperty(GenAiErrorProperties.RETRY_AFTER_MILLIS, Long.class))
                .isEqualTo(5_000L);
    }
}
