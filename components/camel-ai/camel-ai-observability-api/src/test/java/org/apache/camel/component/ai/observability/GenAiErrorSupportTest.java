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
package org.apache.camel.component.ai.observability;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiErrorSupportTest extends CamelTestSupport {

    @Test
    void shouldClassifyLangChain4jRateLimitException() {
        assertThat(GenAiErrorSupport.classify(new RateLimitException("quota exceeded")))
                .isEqualTo(GenAiErrorCategory.RATE_LIMIT);
    }

    @Test
    void shouldClassifyLangChain4jAuthenticationException() {
        assertThat(GenAiErrorSupport.classify(new AuthenticationException("invalid key")))
                .isEqualTo(GenAiErrorCategory.AUTH);
    }

    @Test
    void shouldClassifyLangChain4jValidationExceptions() {
        assertThat(GenAiErrorSupport.classify(new InvalidRequestException("bad request")))
                .isEqualTo(GenAiErrorCategory.VALIDATION);
    }

    @Test
    void shouldClassifyLangChain4jHttpStatusCodes() {
        assertThat(GenAiErrorSupport.classify(new HttpException(429, "too many requests")))
                .isEqualTo(GenAiErrorCategory.RATE_LIMIT);
        assertThat(GenAiErrorSupport.classify(new HttpException(401, "unauthorized")))
                .isEqualTo(GenAiErrorCategory.AUTH);
        assertThat(GenAiErrorSupport.classify(new HttpException(503, "unavailable")))
                .isEqualTo(GenAiErrorCategory.SERVER_ERROR);
        assertThat(GenAiErrorSupport.classify(new HttpException(408, "timeout")))
                .isEqualTo(GenAiErrorCategory.SERVER_ERROR);
        assertThat(GenAiErrorSupport.classify(new HttpException(400, "bad request")))
                .isEqualTo(GenAiErrorCategory.VALIDATION);
    }

    @Test
    void shouldClassifyLangChain4jInternalServerException() {
        assertThat(GenAiErrorSupport.classify(new InternalServerException("upstream failure")))
                .isEqualTo(GenAiErrorCategory.SERVER_ERROR);
    }

    @Test
    void shouldWalkCauseChain() {
        RuntimeException wrapped = new RuntimeException("outer", new RateLimitException("429"));
        assertThat(GenAiErrorSupport.classify(wrapped)).isEqualTo(GenAiErrorCategory.RATE_LIMIT);
    }

    @Test
    void shouldClassifySpringAiExceptionsByClassName() {
        assertThat(GenAiErrorSupport.classify(new org.springframework.ai.retry.TransientAiException("retry")))
                .isEqualTo(GenAiErrorCategory.SERVER_ERROR);
        assertThat(GenAiErrorSupport.classify(new org.springframework.ai.retry.NonTransientAiException("fail")))
                .isEqualTo(GenAiErrorCategory.VALIDATION);
    }

    @Test
    void shouldPreferSpecificCauseOverSpringAiWrapper() {
        assertThat(GenAiErrorSupport.classify(
                new org.springframework.ai.retry.TransientAiException("retry", new RateLimitException("429"))))
                .isEqualTo(GenAiErrorCategory.RATE_LIMIT);
    }

    @Test
    void shouldApplyCategoryPropertyToExchange() {
        DefaultExchange exchange = new DefaultExchange(context);
        GenAiErrorSupport.apply(exchange, new RateLimitException("quota exceeded"));

        assertThat(exchange.getProperty(GenAiErrorProperties.ERROR_CATEGORY, String.class))
                .isEqualTo(GenAiErrorCategory.RATE_LIMIT.name());
        assertThat(exchange.getProperty(GenAiErrorProperties.RETRY_AFTER_MILLIS)).isNull();
    }

    @Test
    void shouldReturnUnknownForUnrecognizedException() {
        assertThat(GenAiErrorSupport.classify(new IllegalStateException("boom")))
                .isEqualTo(GenAiErrorCategory.UNKNOWN);
    }
}
