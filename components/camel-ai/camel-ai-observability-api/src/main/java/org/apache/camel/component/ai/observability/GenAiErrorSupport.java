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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Populates exchange properties with structured AI error metadata before an exception propagates.
 * <p/>
 * This runs independently of GenAI observability spans so routes can react in {@code onException} even when
 * {@code camel-ai-observability} is absent or disabled.
 *
 * @since 4.23
 */
public final class GenAiErrorSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GenAiErrorSupport.class);

    private static final Map<String, GenAiErrorCategory> LANGCHAIN4J_EXCEPTION_CATEGORIES = Map.ofEntries(
            Map.entry("dev.langchain4j.exception.RateLimitException", GenAiErrorCategory.RATE_LIMIT),
            Map.entry("dev.langchain4j.exception.InternalServerException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("dev.langchain4j.exception.TimeoutException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("dev.langchain4j.exception.UnresolvedModelServerException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("dev.langchain4j.exception.RetriableException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("dev.langchain4j.exception.AuthenticationException", GenAiErrorCategory.AUTH),
            Map.entry("dev.langchain4j.exception.InvalidRequestException", GenAiErrorCategory.VALIDATION),
            Map.entry("dev.langchain4j.exception.ContentFilteredException", GenAiErrorCategory.VALIDATION),
            Map.entry("dev.langchain4j.exception.ModelNotFoundException", GenAiErrorCategory.VALIDATION),
            Map.entry("dev.langchain4j.exception.ToolArgumentsException", GenAiErrorCategory.VALIDATION));

    private static final Map<String, GenAiErrorCategory> OPENAI_EXCEPTION_CATEGORIES = Map.ofEntries(
            Map.entry("com.openai.errors.RateLimitException", GenAiErrorCategory.RATE_LIMIT),
            Map.entry("com.openai.errors.InternalServerException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("com.openai.errors.BadRequestException", GenAiErrorCategory.VALIDATION),
            Map.entry("com.openai.errors.UnprocessableEntityException", GenAiErrorCategory.VALIDATION),
            Map.entry("com.openai.errors.NotFoundException", GenAiErrorCategory.VALIDATION),
            Map.entry("com.openai.errors.UnauthorizedException", GenAiErrorCategory.AUTH),
            Map.entry("com.openai.errors.PermissionDeniedException", GenAiErrorCategory.AUTH),
            Map.entry("com.openai.errors.OpenAIRetryableException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("com.openai.errors.OpenAIIoException", GenAiErrorCategory.SERVER_ERROR),
            Map.entry("com.openai.errors.OpenAIInvalidDataException", GenAiErrorCategory.VALIDATION));

    private static final Map<String, GenAiErrorCategory> SPRING_AI_EXCEPTION_CATEGORIES = Map.of(
            "org.springframework.ai.retry.TransientAiException", GenAiErrorCategory.SERVER_ERROR,
            "org.springframework.ai.retry.NonTransientAiException", GenAiErrorCategory.VALIDATION);

    private static final Set<String> RETRY_AFTER_MS_HEADER_NAMES = Set.of("Retry-After-Ms", "retry-after-ms");
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
        try {
            GenAiErrorCategory category = classify(error);
            exchange.setProperty(GenAiErrorProperties.ERROR_CATEGORY, category.name());
            Long retryAfterMillis = extractRetryAfterMillis(error);
            if (retryAfterMillis != null) {
                exchange.setProperty(GenAiErrorProperties.RETRY_AFTER_MILLIS, retryAfterMillis);
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to populate GenAI error metadata on exchange", e);
            }
        }
    }

    /**
     * Classifies an AI provider failure, walking the exception cause chain.
     * <p/>
     * Classification runs in two passes over the cause chain: first with provider-specific mappings (LangChain4j,
     * OpenAI, and HTTP status codes), then with generic Spring AI retry wrappers. That ordering ensures a
     * {@code TransientAiException} wrapping a {@code RateLimitException} is classified as {@code RATE_LIMIT} from the
     * specific cause rather than {@code SERVER_ERROR} from the coarse Spring AI wrapper.
     */
    public static GenAiErrorCategory classify(Throwable error) {
        GenAiErrorCategory category = classifyChain(error, false);
        if (category != GenAiErrorCategory.UNKNOWN) {
            return category;
        }
        return classifyChain(error, true);
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

    private static GenAiErrorCategory classifyChain(Throwable error, boolean allowSpringAiGeneric) {
        Throwable current = error;
        while (current != null) {
            GenAiErrorCategory category = classifySingle(current, allowSpringAiGeneric);
            if (category != GenAiErrorCategory.UNKNOWN) {
                return category;
            }
            current = current.getCause();
        }
        return GenAiErrorCategory.UNKNOWN;
    }

    private static GenAiErrorCategory classifySingle(Throwable error, boolean allowSpringAiGeneric) {
        GenAiErrorCategory langChain4jCategory = classifyByHierarchy(error, LANGCHAIN4J_EXCEPTION_CATEGORIES);
        if (langChain4jCategory != GenAiErrorCategory.UNKNOWN) {
            return langChain4jCategory;
        }
        if ("dev.langchain4j.exception.HttpException".equals(error.getClass().getName())) {
            return fromHttpStatus(invokeIntMethod(error, "statusCode"));
        }

        GenAiErrorCategory openAiCategory = classifyByHierarchy(error, OPENAI_EXCEPTION_CATEGORIES);
        if (openAiCategory != GenAiErrorCategory.UNKNOWN) {
            return openAiCategory;
        }
        if (error.getClass().getName().startsWith("com.openai.errors.")) {
            return fromHttpStatus(invokeIntMethod(error, "statusCode"));
        }

        if (allowSpringAiGeneric) {
            GenAiErrorCategory springAiCategory = classifyByHierarchy(error, SPRING_AI_EXCEPTION_CATEGORIES);
            if (springAiCategory != GenAiErrorCategory.UNKNOWN) {
                return springAiCategory;
            }
        }

        return GenAiErrorCategory.UNKNOWN;
    }

    private static GenAiErrorCategory classifyByHierarchy(Throwable error, Map<String, GenAiErrorCategory> categories) {
        Class<?> type = error.getClass();
        while (type != null && Throwable.class.isAssignableFrom(type)) {
            GenAiErrorCategory category = categories.get(type.getName());
            if (category != null) {
                return category;
            }
            type = type.getSuperclass();
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
        if (statusCode == 408 || statusCode >= 500) {
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
            Long retryAfterMs = readRetryAfterMillisHeader(headers);
            if (retryAfterMs != null) {
                return retryAfterMs;
            }
            return readRetryAfterSecondsHeader(headers);
        } catch (ReflectiveOperationException ignored) {
            // OpenAI SDK not present or API changed
        }
        return null;
    }

    private static Long readRetryAfterMillisHeader(Object headers) throws ReflectiveOperationException {
        Method valuesMethod = headers.getClass().getMethod("values", String.class);
        Method namesMethod = headers.getClass().getMethod("names");
        Object namesObject = namesMethod.invoke(headers);
        if (!(namesObject instanceof Set<?> names)) {
            return readNamedHeaderValues(valuesMethod, headers, RETRY_AFTER_MS_HEADER_NAMES);
        }
        for (Object nameObject : names) {
            if (nameObject == null) {
                continue;
            }
            String name = nameObject.toString();
            if (!isRetryAfterMsHeader(name)) {
                continue;
            }
            Long parsed = readFirstHeaderValue(valuesMethod, headers, name);
            if (parsed != null) {
                return parsed;
            }
        }
        return readNamedHeaderValues(valuesMethod, headers, RETRY_AFTER_MS_HEADER_NAMES);
    }

    private static Long readRetryAfterSecondsHeader(Object headers) throws ReflectiveOperationException {
        Method valuesMethod = headers.getClass().getMethod("values", String.class);
        Method namesMethod = headers.getClass().getMethod("names");
        Object namesObject = namesMethod.invoke(headers);
        if (namesObject instanceof Set<?> names) {
            for (Object nameObject : names) {
                if (nameObject == null) {
                    continue;
                }
                String name = nameObject.toString();
                if (!isRetryAfterHeader(name)) {
                    continue;
                }
                Long parsed = parseRetryAfterSeconds(readRawHeaderValue(valuesMethod, headers, name));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return readNamedHeaderValues(valuesMethod, headers, RETRY_AFTER_HEADER_NAMES);
    }

    private static Long readNamedHeaderValues(Method valuesMethod, Object headers, Set<String> headerNames)
            throws ReflectiveOperationException {
        for (String headerName : headerNames) {
            Long parsed = readFirstHeaderValue(valuesMethod, headers, headerName);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Long readFirstHeaderValue(Method valuesMethod, Object headers, String headerName)
            throws ReflectiveOperationException {
        if (isRetryAfterMsHeader(headerName)) {
            return parseRetryAfterMillis(readRawHeaderValue(valuesMethod, headers, headerName));
        }
        return parseRetryAfterSeconds(readRawHeaderValue(valuesMethod, headers, headerName));
    }

    private static Object readRawHeaderValue(Method valuesMethod, Object headers, String headerName)
            throws ReflectiveOperationException {
        Object valuesObject = valuesMethod.invoke(headers, headerName);
        if (!(valuesObject instanceof List<?> values) || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private static boolean isRetryAfterMsHeader(String headerName) {
        return "retry-after-ms".equals(headerName.toLowerCase(Locale.ROOT));
    }

    private static boolean isRetryAfterHeader(String headerName) {
        return "retry-after".equals(headerName.toLowerCase(Locale.ROOT));
    }

    private static Long parseRetryAfterMillis(Object headerValue) {
        if (headerValue == null) {
            return null;
        }
        String value = headerValue.toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            long millis = Long.parseLong(value);
            return millis < 0 ? null : millis;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parseRetryAfterSeconds(Object headerValue) {
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
            if (seconds > Long.MAX_VALUE / 1000L) {
                return Long.MAX_VALUE;
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
