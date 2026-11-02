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
package org.apache.camel.component.chaos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.spi.SendHook;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;

/**
 * What the faults do to an exchange: the exception of {@code error} (also the failure of an HTTP component for
 * {@code status=}), and the changes of {@code corrupt}.
 */
final class ChaosFaults {

    static final String HTTP_FAILED = "org.apache.camel.http.base.HttpOperationFailedException";

    private static final char[] RANDOM_CHARS
            = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int RANDOM_LENGTH = 16;

    private ChaosFaults() {
    }

    /**
     * The exception of an {@code error} rule: an instance of {@code exception=}, or else a {@link ChaosException}, with
     * {@code message=} or a default message.
     */
    static Throwable newException(ChaosRule rule) {
        String message = rule.message != null ? rule.message : "Chaos error of rule " + rule.id;
        if (rule.exceptionConstructor == null) {
            return new ChaosException(message);
        }
        try {
            return rule.exceptionTakesMessage
                    ? rule.exceptionConstructor.newInstance(message) : rule.exceptionConstructor.newInstance();
        } catch (Exception | LinkageError e) {
            return new ChaosException(
                    "Chaos rule " + rule.id + " could not create the exception " + rule.exceptionType.getName(), e);
        }
    }

    /**
     * Raises the fault of a rule that fired at a send.
     *
     * @return the value for {@link SendHook#beforeSend(Exchange, Endpoint)}
     */
    static long atSend(ChaosRule rule, Exchange exchange, Endpoint endpoint) {
        switch (rule.fault) {
            case ERROR -> {
                if (rule.status > 0) {
                    return httpFailure(rule, exchange, endpoint);
                }
                exchange.setException(newException(rule));
                rule.fired(Hook.SEND);
                return SendHook.PROCEED;
            }
            case DELAY -> {
                rule.fired(Hook.SEND);
                return rule.duration;
            }
            case DROP -> {
                rule.fired(Hook.SEND);
                return SendHook.SKIP;
            }
            case CORRUPT -> {
                corrupt(rule, exchange, Hook.SEND);
                return SendHook.PROCEED;
            }
            default -> throw new IllegalStateException("Unknown fault " + rule.fault);
        }
    }

    /**
     * The failure of an HTTP component for the status of the rule, or the response with that status when the endpoint
     * has {@code throwExceptionOnFailure=false}, as the producer of the component does. The producer is not called.
     */
    private static long httpFailure(ChaosRule rule, Exchange exchange, Endpoint endpoint) {
        String scheme = ChaosPatterns.scheme(endpoint.getEndpointUri());
        if (!ChaosRuleParser.HTTP_SCHEMES.contains(scheme)) {
            // the rule matched an endpoint that is not an HTTP one (with a pattern such as endpoint=*inventory*)
            rule.skipped();
            return SendHook.PROCEED;
        }
        Message message = exchange.getMessage();
        // camel-undertow has no reason phrase on its messages; the other HTTP components set it
        boolean undertow = "undertow".equals(scheme);
        if (Boolean.FALSE.equals(toBoolean(option(endpoint, "throwExceptionOnFailure")))
                || isOkStatus(rule.status, option(endpoint, "okStatusCodeRange"))) {
            response(message, rule, !undertow);
            rule.fired(Hook.SEND);
            return SendHook.SKIP;
        }
        Throwable failure;
        try {
            failure = httpFailure(rule, endpoint);
        } catch (Exception | LinkageError e) {
            failure = new ChaosException(
                    "Chaos rule " + rule.id + " could not create the failure of " + scheme + " for status " + rule.status, e);
        }
        if ("http".equals(scheme) || "https".equals(scheme)) {
            // camel-http stores the status (and the reason phrase) on the message before it throws too
            setStatus(message, rule.status, true);
        } else if (undertow) {
            // camel-undertow fails with the response as the message
            response(message, rule, false);
        }
        exchange.setException(failure);
        rule.fired(Hook.SEND);
        return SendHook.PROCEED;
    }

    private static Throwable httpFailure(ChaosRule rule, Endpoint endpoint) throws Exception {
        // HttpOperationFailedException(uri, statusCode, statusText, location, responseHeaders, responseBody). The uri
        // is the one of the endpoint without its options (which can hold credentials)
        Class<?> type = resolve(endpoint, HTTP_FAILED);
        return (Throwable) type.getConstructor(String.class, int.class, String.class, String.class, Map.class, String.class)
                .newInstance(endpoint.getEndpointBaseUri(), rule.status, statusText(rule.status), location(rule),
                        rule.responseHeaders,
                        rule.responseBody != null ? rule.responseBody : "");
    }

    private static Class<?> resolve(Endpoint endpoint, String name) throws ClassNotFoundException {
        return endpoint.getCamelContext().getClassResolver().resolveMandatoryClass(name);
    }

    /** The response of the rule on the message: the status, the header.* values and the body (empty without it). */
    private static void response(Message message, ChaosRule rule, boolean reasonPhrase) {
        setStatus(message, rule.status, reasonPhrase);
        rule.responseHeaders.forEach(message::setHeader);
        message.setBody(rule.responseBody != null ? rule.responseBody : "");
    }

    private static void setStatus(Message message, int status, boolean reasonPhrase) {
        message.setHeader(Exchange.HTTP_RESPONSE_CODE, status);
        String text = reasonPhrase ? statusText(status) : "";
        if (!text.isEmpty()) {
            message.setHeader(Exchange.HTTP_RESPONSE_TEXT, text);
        } else {
            // no reason phrase: no text from an earlier response either
            message.removeHeader(Exchange.HTTP_RESPONSE_TEXT);
        }
    }

    /** The reason phrase of an HTTP status, as a server sends it, or an empty text for a less common status. */
    static String statusText(int status) {
        return switch (status) {
            case 300 -> "Multiple Choices";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 406 -> "Not Acceptable";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 412 -> "Precondition Failed";
            case 413 -> "Content Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Content";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
    }

    private static String location(ChaosRule rule) {
        for (Map.Entry<String, String> header : rule.responseHeaders.entrySet()) {
            if ("Location".equalsIgnoreCase(header.getKey())) {
                return header.getValue();
            }
        }
        return null;
    }

    /**
     * The value of an option of the endpoint, read through the configurer of its component (so an option set on the
     * component counts too), or null when the endpoint has no such option or no configurer.
     */
    static Object option(Endpoint endpoint, String name) {
        Component component = endpoint.getComponent();
        PropertyConfigurer configurer = component != null ? component.getEndpointPropertyConfigurer() : null;
        if (configurer instanceof PropertyConfigurerGetter getter) {
            try {
                return getter.getOptionValue(endpoint, name, true);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Whether the status is in the okStatusCodeRange of the endpoint (such as {@code 200-299} or {@code 200-299,404}),
     * for which the producer returns the response instead of failing. False without the option, or when it cannot be
     * read. It reads the range as {@code HttpHelper.isStatusCodeOk} of camel-http-base does (camel-chaos does not
     * depend on it).
     */
    static boolean isOkStatus(int status, Object range) {
        if (range == null) {
            return false;
        }
        for (String part : range.toString().split(",")) {
            String p = part.trim();
            int dash = p.indexOf('-');
            try {
                if (dash > 0) {
                    int from = Integer.parseInt(p.substring(0, dash).trim());
                    int to = Integer.parseInt(p.substring(dash + 1).trim());
                    if (status >= from && status <= to) {
                        return true;
                    }
                } else if (!p.isEmpty() && Integer.parseInt(p) == status) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // not a range the producer would accept either
            }
        }
        return false;
    }

    static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value != null) {
            String text = value.toString().trim();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * Changes the message of the exchange as the mode of the rule says, and counts the rule as fired at the hook, or as
     * skipped when the message has nothing the mode can change.
     */
    static void corrupt(ChaosRule rule, Exchange exchange, Hook hook) {
        boolean changed;
        try {
            changed = switch (rule.mode) {
                case HEADERS -> corruptHeaders(rule, exchange.getMessage());
                case CHARSET -> corruptCharset(exchange);
                case TRUNCATE -> truncate(exchange.getMessage());
            };
        } catch (RuntimeException e) {
            // a body that cannot be read as the mode needs (such as a failing type conversion) is left as it is
            changed = false;
        }
        if (changed) {
            rule.fired(hook);
        } else {
            rule.skipped();
        }
    }

    /**
     * Gives every header that is not one of Camel's own ({@code Camel*}) a random value of letters and digits, from the
     * random sequence of the rule.
     */
    private static boolean corruptHeaders(ChaosRule rule, Message message) {
        List<String> names = new ArrayList<>();
        for (String name : message.getHeaders().keySet()) {
            if (!name.regionMatches(true, 0, "Camel", 0, 5)) {
                names.add(name);
            }
        }
        for (String name : names) {
            char[] value = new char[RANDOM_LENGTH];
            for (int i = 0; i < value.length; i++) {
                value[i] = RANDOM_CHARS[(int) ((rule.nextLong() >>> 1) % RANDOM_CHARS.length)];
            }
            message.setHeader(name, new String(value));
        }
        return !names.isEmpty();
    }

    /**
     * Replaces a text body (a String, or a body that converts to text: also a local file, read into memory; not a
     * stream, which reading would consume) with the bytes of that text in UTF-16, while the exchange keeps its charset:
     * read as text again, the body has characters outside US-ASCII.
     */
    private static boolean corruptCharset(Exchange exchange) {
        Message message = exchange.getMessage();
        Object body = message.getBody();
        if (body == null || body instanceof InputStream || body instanceof Reader || body instanceof StreamCache) {
            return false;
        }
        if (body instanceof WrappedFile<?> wrapped && !(wrapped.getFile() instanceof File)) {
            // a remote file (such as FTP), whose content may be a stream
            return false;
        }
        String text;
        if (body instanceof CharSequence cs) {
            text = cs.toString();
        } else if (body instanceof byte[] bytes) {
            text = new String(bytes, ExchangeHelper.getCharset(exchange));
        } else {
            text = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, body);
        }
        if (text == null || text.isEmpty()) {
            return false;
        }
        message.setBody(text.getBytes(StandardCharsets.UTF_16));
        return true;
    }

    /**
     * Replaces an {@link InputStream} or stream cache body with one that ends after the first half of its bytes. The
     * stream is read into memory and closed; a stream cache stays a stream cache.
     */
    private static boolean truncate(Message message) {
        Object body = message.getBody();
        boolean cache = body instanceof StreamCache;
        if (!(body instanceof InputStream) && !cache) {
            return false;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (body instanceof StreamCache streamCache) {
                streamCache.reset();
                streamCache.writeTo(bytes);
            } else {
                ((InputStream) body).transferTo(bytes);
            }
        } catch (IOException e) {
            // the stream failed by itself: keep what could be read
        } finally {
            if (body instanceof InputStream in) {
                IOHelper.close(in);
            }
        }
        byte[] all = bytes.toByteArray();
        byte[] half = new byte[all.length / 2];
        System.arraycopy(all, 0, half, 0, half.length);
        message.setBody(cache ? new InputStreamCache(half) : new ByteArrayInputStream(half));
        return all.length > 0;
    }
}
