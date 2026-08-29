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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;

/**
 * Populates exchange properties with structured AI error metadata before an exception propagates.
 * <p/>
 * This runs independently of GenAI observability spans so routes can react in {@code onException} even when
 * {@code camel-ai-observability} is absent or disabled.
 *
 * @since 4.23
 */
public final class GenAiErrorSupport {

    private static final Map<String, GenAiErrorCategory> LANGCHAIN4J_EXCEPTION_CATEGORIES = Map.ofEntries(
            Map.entry("dev.langchain4j.exception.RateLimitException", GenAiErrorCategory.RATE_LIMIT),
            Map.entry("dev.langchain4j.exception.InternalServerException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("dev.langchain4j.exception.TimeoutException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("dev.langchain4j.exception.AuthenticationException", GenAiErrorCategory.AUTH),
            Map.entry("dev.langchain4j.exception.InvalidRequestException", GenAiErrorCategory.VALIDATION),
            Map.entry("dev.langchain4j.exception.ContentFilteredException", GenAiErrorCategory.VALIDATION),
            Map.entry("dev.langchain4j.exception.ModelNotFoundException", GenAiErrorCategory.VALIDATION));

    private static final Map<String, GenAiErrorCategory> OPENAI_EXCEPTION_CATEGORIES = Map.ofEntries(
            Map.entry("com.openai.errors.RateLimitException", GenAiErrorCategory.RATE_LIMIT),
            Map.entry("com.openai.errors.InternalServerException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("com.openai.errors.BadRequestException", GenAiErrorCategory.VALIDATION),
            Map.entry("com.openai.errors.UnprocessableEntityException", GenAiErrorCategory.VALIDATION),
            Map.entry("com.openai.errors.NotFoundException", GenAiErrorCategory.VALIDATION),
            Map.entry("com.openai.errors.UnauthorizedException", GenAiErrorCategory.AUTH),
            Map.entry("com.openai.errors.PermissionDeniedException", GenAiErrorCategory.AUTH));

    private static final Map<String, GenAiErrorCategory> SPRING_AI_EXCEPTION_CATEGORIES = Map.of(
            "org.springframework.ai.retry.TransientAiException", GenAiErrorCategory.SERVER_ERROR,
            "org.springframework.ai.retry.NonTransientAiException", GenAiErrorCategory.VALIDATION);

    private static final Set<String> RETRY_AFTER_HEADER_NAMES = Set.of("Retry-After", "retry-after");

    private GenAiErrorSupport() {
    }

    /**
     * Sets {@link GenAiErrorProperties#ERROR_CATEGORY} and, when available,
     * {@link GenAiErrorProperties#RETRY_AFTER_MILLIS} on the exchange.
     */
    public static void apply(Exchange exchange, Throwable error) {
        if (exchange == null || error == null) {
            return;
        }
        GenAiErrorCategory category = classify(error);
        exchange.setProperty(GenAiErrorProperties.ERROR_CATEGORY, category.name());
        Long retryAfterMillis = extractRetryAfterMillis(error);
        if (retryAfterMillis != null) {
            exchange.setProperty(GenAiErrorProperties.RETRY_AFTER_MILLIS, retryAfterMillis);
        }
    }

    /**
     * Classifies an AI provider failure, walking the exception cause chain.
     */
    public static GenAiErrorCategory classify(Throwable error) {
        Throwable current = error;
        while (current != null) {
            GenAiErrorCategory category = classifySingle(current);
            if (category != GenAiErrorCategory.UNKNOWN) {
                return category;
            }
            current = current.getCause();
        }
        return GenAiErrorCategory.UNKNOWN;
    }

    /**
     * Extracts retry delay in milliseconds when the provider exposes {@code Retry-After}. Currently populated for
     * OpenAI {@code OpenAIServiceException} responses only.
     */
    public static Long extractRetryAfterMillis(Throwable error) {
        Throwable current = error;
        while (current != null) {
            Long retryAfter = extractOpenAiRetryAfterMillis(current);
            if (retryAfter != null) {
                return retryAfter;
            }
            current = current.getCause();
        }
        return null;
    }

    private static GenAiErrorCategory classifySingle(Throwable error) {
        String className = error.getClass().getName();

        GenAiErrorCategory langChain4jCategory = LANGCHAIN4J_EXCEPTION_CATEGORIES.get(className);
        if (langChain4jCategory != null) {
            return langChain4jCategory;
        }
        if ("dev.langchain4j.exception.HttpException".equals(className)) {
            return fromHttpStatus(invokeIntMethod(error, "statusCode"));
        }

        GenAiErrorCategory openAiCategory = OPENAI_EXCEPTION_CATEGORIES.get(className);
        if (openAiCategory != null) {
            return openAiCategory;
        }
        if (className.startsWith("com.openai.errors.")) {
            return fromHttpStatus(invokeIntMethod(error, "statusCode"));
        }

        GenAiErrorCategory springAiCategory = SPRING_AI_EXCEPTION_CATEGORIES.get(className);
        if (springAiCategory != null) {
            return springAiCategory;
        }

        return GenAiErrorCategory.UNKNOWN;
    }

    private static GenAiErrorCategory fromHttpStatus(int statusCode) {
        if (statusCode == 429) {
            return GenAiErrorCategory.RATE_LIMIT;
        }
        if (statusCode == 401 || statusCode == 403) {
            return GenAiErrorCategory.AUTH;
        }
        if (statusCode >= 500) {
            return GenAiErrorCategory.SERVER_ERROR;
        }
        if (statusCode >= 400) {
            return GenAiErrorCategory.VALIDATION;
        }
        return GenAiErrorCategory.UNKNOWN;
    }

    private static Long extractOpenAiRetryAfterMillis(Throwable error) {
        if (!error.getClass().getName().startsWith("com.openai.errors.")) {
            return null;
        }
        try {
            Method headersMethod = error.getClass().getMethod("headers");
            Object headers = headersMethod.invoke(error);
            if (headers == null) {
                return null;
            }
            Method valuesMethod = headers.getClass().getMethod("values", String.class);
            for (String headerName : RETRY_AFTER_HEADER_NAMES) {
                Object valuesObject = valuesMethod.invoke(headers, headerName);
                if (!(valuesObject instanceof List<?> values) || values.isEmpty()) {
                    continue;
                }
                Long parsed = parseRetryAfterHeader(values.get(0));
                if (parsed != null) {
                    return parsed;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // OpenAI SDK not present or API changed
        }
        return null;
    }

    private static Long parseRetryAfterHeader(Object headerValue) {
        if (headerValue == null) {
            return null;
        }
        String value = headerValue.toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value);
            if (seconds < 0) {
                return null;
            }
            return seconds * 1000L;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int invokeIntMethod(Throwable target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            if (result instanceof Number number) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return 0;
    }
}
