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
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
import org.apache.camel.util.TimeSource;

/**
 * What the faults do to an exchange: the exception of {@code error} (also the failure of an HTTP component for
 * {@code status=}), and the changes of {@code corrupt}.
 */
final class ChaosFaults {

    static final String HTTP_FAILED = "org.apache.camel.http.base.HttpOperationFailedException";
    static final String NETTY_HTTP_FAILED = "org.apache.camel.component.netty.http.NettyHttpOperationFailedException";

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
     * Raises the fault of a rule that fired at a send: at the {@code send} hook, or the {@code dlq} hook for the send
     * of the dead letter channel.
     *
     * @return the value for {@link SendHook#beforeSend(Exchange, Endpoint)}
     */
    static long atSend(ChaosRule rule, Exchange exchange, Endpoint endpoint, Hook hook) {
        switch (rule.fault) {
            case ERROR -> {
                if (rule.status > 0) {
                    return httpFailure(rule, exchange, endpoint, hook);
                }
                rule.fired(hook, exchange, null, endpoint.getEndpointUri());
                if (waitBeforeError(rule, exchange)) {
                    exchange.setException(newException(rule));
                }
                return SendHook.PROCEED;
            }
            case DELAY -> {
                rule.fired(hook, exchange, null, endpoint.getEndpointUri());
                return rule.delayFor();
            }
            case DROP -> {
                rule.fired(hook, exchange, null, endpoint.getEndpointUri());
                return SendHook.SKIP;
            }
            case CORRUPT -> {
                corrupt(rule, exchange, hook, null, endpoint.getEndpointUri());
                return SendHook.PROCEED;
            }
            case DUPLICATE -> {
                // the message is sent twice, as a sender that delivers it again
                rule.fired(hook, exchange, null, endpoint.getEndpointUri());
                return SendHook.SEND_TWICE;
            }
            default -> throw new IllegalStateException("Unknown fault " + rule.fault);
        }
    }

    /**
     * Raises the fault of a rule that fired at the reply of a send, after the producer succeeded: the side effect of
     * the send happened, and then the send fails ({@code error}, also the failure of an HTTP component for
     * {@code status=}), or the reply is changed ({@code corrupt}).
     */
    static void atReply(ChaosRule rule, Exchange exchange, Endpoint endpoint) {
        switch (rule.fault) {
            case ERROR -> {
                if (rule.status > 0) {
                    httpFailure(rule, exchange, endpoint, Hook.REPLY);
                    return;
                }
                rule.fired(Hook.REPLY, exchange, null, endpoint.getEndpointUri());
                exchange.setException(newException(rule));
            }
            case CORRUPT -> corrupt(rule, exchange, Hook.REPLY, null, endpoint.getEndpointUri());
            default -> throw new IllegalStateException("The fault " + rule.fault + " does not fire at the reply");
        }
    }

    /**
     * Waits the {@code after=} time of an error rule before it fails, as for a call that times out. When the wait is
     * interrupted, the exchange fails with the {@link InterruptedException} instead (and the thread keeps its
     * interrupted status), as for a delay of the send hook.
     *
     * @return whether to raise the error now (false when the wait was interrupted)
     */
    static boolean waitBeforeError(ChaosRule rule, Exchange exchange) {
        if (rule.after <= 0) {
            return true;
        }
        try {
            TimeSource.sleep(rule.after);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.setException(e);
            return false;
        }
    }

    /**
     * The failure of an HTTP component for the status of the rule, or the response with that status when the endpoint
     * has {@code throwExceptionOnFailure=false}, as the producer of the component does. The producer is not called.
     */
    private static long httpFailure(ChaosRule rule, Exchange exchange, Endpoint endpoint, Hook hook) {
        String scheme = ChaosPatterns.scheme(ChaosPatterns.endpointUri(endpoint));
        if (!ChaosRuleParser.HTTP_SCHEMES.contains(scheme)) {
            // the rule matched an endpoint that is not an HTTP one (with a pattern such as endpoint=*inventory*)
            rule.skipped("not an HTTP endpoint (status= is for the HTTP components)");
            return SendHook.PROCEED;
        }
        rule.fired(hook, exchange, null, endpoint.getEndpointUri());
        if (!waitBeforeError(rule, exchange)) {
            return SendHook.PROCEED;
        }
        Message message = exchange.getMessage();
        // camel-undertow has no reason phrase on its messages; the other HTTP components set it
        boolean undertow = "undertow".equals(scheme);
        boolean netty = "netty-http".equals(scheme);
        if (Boolean.FALSE.equals(toBoolean(option(endpoint, "throwExceptionOnFailure")))
                || isOkStatus(rule.status, option(endpoint, "okStatusCodeRange"))) {
            response(message, rule, !undertow);
            return SendHook.SKIP;
        }
        Throwable failure;
        try {
            failure = netty ? nettyHttpFailure(rule, endpoint) : httpFailure(rule, endpoint, scheme);
        } catch (InvocationTargetException e) {
            // a constructor or method of the component refused a value of the rule (such as a header value)
            failure = new ChaosException(
                    "Chaos rule " + rule.id + " could not create the failure of " + scheme + " for status " + rule.status,
                    e.getCause());
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
        } else if (netty) {
            // camel-netty-http fails with the response as the message, with its reason phrase
            response(message, rule, true);
        }
        exchange.setException(failure);
        return SendHook.PROCEED;
    }

    private static Throwable httpFailure(ChaosRule rule, Endpoint endpoint, String scheme) throws Exception {
        // HttpOperationFailedException(uri, statusCode, statusText, location, responseHeaders, responseBody). The uri
        // is the one of the endpoint without its options (which can hold credentials). camel-undertow never gives a
        // location, and camel-vertx-http only the one of a redirect it followed
        boolean withLocation = "http".equals(scheme) || "https".equals(scheme);
        Class<?> type = resolve(endpoint, HTTP_FAILED);
        return (Throwable) type.getConstructor(String.class, int.class, String.class, String.class, Map.class, String.class)
                .newInstance(endpoint.getEndpointBaseUri(), rule.status, statusText(rule.status),
                        withLocation ? location(rule) : null,
                        rule.responseHeaders,
                        rule.responseBody != null ? rule.responseBody : "");
    }

    private static Throwable nettyHttpFailure(ChaosRule rule, Endpoint endpoint) throws Exception {
        // NettyHttpOperationFailedException(uri, statusCode, statusText, location, content), with the response as its
        // content, as camel-netty-http gives it: a FullHttpResponse with the status, the header.* values and the body.
        // Through the class resolver, as camel-chaos does not depend on camel-netty-http (nor on Netty). The body is
        // wrapped in an unpooled heap buffer, which needs no release
        byte[] body = (rule.responseBody != null ? rule.responseBody : "").getBytes(StandardCharsets.UTF_8);
        Class<?> bufType = resolve(endpoint, "io.netty.buffer.ByteBuf");
        Class<?> versionType = resolve(endpoint, "io.netty.handler.codec.http.HttpVersion");
        Class<?> statusType = resolve(endpoint, "io.netty.handler.codec.http.HttpResponseStatus");
        Object content = resolve(endpoint, "io.netty.buffer.Unpooled").getMethod("wrappedBuffer", byte[].class)
                .invoke(null, (Object) body);
        Object status = statusType.getMethod("valueOf", int.class, String.class)
                .invoke(null, rule.status, statusText(rule.status));
        Object response = resolve(endpoint, "io.netty.handler.codec.http.DefaultFullHttpResponse")
                .getConstructor(versionType, statusType, bufType)
                .newInstance(versionType.getField("HTTP_1_1").get(null), status, content);
        Class<?> headersType = resolve(endpoint, "io.netty.handler.codec.http.HttpHeaders");
        Object headers = response.getClass().getMethod("headers").invoke(response);
        Method add = headersType.getMethod("add", String.class, Object.class);
        for (Map.Entry<String, String> header : rule.responseHeaders.entrySet()) {
            add.invoke(headers, header.getKey(), header.getValue());
        }
        Class<?> type = resolve(endpoint, NETTY_HTTP_FAILED);
        return (Throwable) type
                .getConstructor(String.class, int.class, String.class, String.class,
                        resolve(endpoint, "io.netty.handler.codec.http.HttpContent"))
                .newInstance(nettyHttpAddress(endpoint), rule.status, statusText(rule.status), location(rule), response);
    }

    /**
     * The address that camel-netty-http calls, as the url of its failure: since CAMEL-26487 the endpoint URI of a
     * camel-netty-http endpoint is its whole URI ({@code netty-http://http://host/path}) and its address is
     * {@code getAddressUri()}.
     */
    private static String nettyHttpAddress(Endpoint endpoint) {
        try {
            return (String) endpoint.getClass().getMethod("getAddressUri").invoke(endpoint);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return endpoint.getEndpointBaseUri();
        }
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
        if (rule.status < 300 || rule.status >= 400) {
            // camel-http and camel-netty-http give the location of a redirect only
            return null;
        }
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
     *
     * @param routeId the route at its entry, else null for the route of the exchange
     * @param where   the endpoint URI of a send, else null
     */
    static void corrupt(ChaosRule rule, Exchange exchange, Hook hook, String routeId, String where) {
        // why the message was left as it is, or null when it was changed
        String skip;
        try {
            skip = switch (rule.mode) {
                case HEADERS -> corruptHeaders(rule, exchange.getMessage());
                case CHARSET -> corruptCharset(exchange);
                case TRUNCATE -> truncate(exchange.getMessage());
                case EMPTY -> empty(exchange.getMessage());
                case REMOVE_HEADER -> removeHeaders(rule, exchange.getMessage());
            };
        } catch (RuntimeException e) {
            // a body that cannot be read as the mode needs (such as a failing type conversion) is left as it is
            skip = "the body could not be read (" + e.getClass().getSimpleName() + ")";
        }
        if (skip == null) {
            rule.fired(hook, exchange, routeId, where);
        } else {
            rule.skipped(skip);
        }
    }

    /**
     * Gives every header that is not one of Camel's own ({@code Camel*}) a random value of letters and digits, from the
     * random sequence of the rule.
     */
    private static String corruptHeaders(ChaosRule rule, Message message) {
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
        return names.isEmpty() ? "no headers to change (Camel* headers are left as they are)" : null;
    }

    /**
     * Replaces a text body (a String, or a body that converts to text: also a local file, read into memory; not a
     * stream, which reading would consume) with the bytes of that text in UTF-16, while the exchange keeps its charset:
     * read as text again, the body has characters outside US-ASCII.
     */
    private static String corruptCharset(Exchange exchange) {
        Message message = exchange.getMessage();
        Object body = message.getBody();
        if (body == null) {
            return "no body";
        }
        if (body instanceof InputStream || body instanceof Reader || body instanceof StreamCache) {
            return "a stream body (charset changes a text body; mode=truncate changes a stream)";
        }
        if (body instanceof WrappedFile<?> wrapped && !(wrapped.getFile() instanceof File)) {
            // a remote file (such as FTP), whose content may be a stream
            return "a remote file body (charset changes a text body)";
        }
        String text;
        if (body instanceof CharSequence cs) {
            text = cs.toString();
        } else if (body instanceof byte[] bytes) {
            text = new String(bytes, ExchangeHelper.getCharset(exchange));
        } else {
            text = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, body);
        }
        if (text == null) {
            return "a body that is not text (charset changes a text body)";
        }
        if (text.isEmpty()) {
            return "an empty body";
        }
        message.setBody(text.getBytes(StandardCharsets.UTF_16));
        return null;
    }

    /**
     * Replaces a String, byte[], {@link InputStream} or stream cache body with one that ends after the first half of
     * its characters or bytes. A stream is read into memory and closed; a stream cache stays a stream cache.
     */
    private static String truncate(Message message) {
        Object body = message.getBody();
        if (body instanceof String text) {
            if (text.isEmpty()) {
                return "an empty body";
            }
            int half = text.length() / 2;
            // not between the two chars of a surrogate pair
            if (half > 0 && Character.isHighSurrogate(text.charAt(half - 1))) {
                half--;
            }
            message.setBody(text.substring(0, half));
            return null;
        }
        if (body instanceof byte[] all) {
            if (all.length == 0) {
                return "an empty body";
            }
            message.setBody(Arrays.copyOf(all, all.length / 2));
            return null;
        }
        boolean cache = body instanceof StreamCache;
        if (!(body instanceof InputStream) && !cache) {
            return body == null
                    ? "no body" : "not a String, byte[] or stream body (truncate changes text, bytes and streams)";
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
        byte[] half = Arrays.copyOf(all, all.length / 2);
        message.setBody(cache ? new InputStreamCache(half) : new ByteArrayInputStream(half));
        return all.length > 0 ? null : "an empty body";
    }

    /**
     * Makes the body empty of its kind: an empty String for text, no bytes for a byte[], an empty stream for a stream
     * (a stream cache stays a stream cache, a reader becomes an empty reader); any other body (such as a POJO or a
     * file) is removed (null).
     */
    private static String empty(Message message) {
        Object body = message.getBody();
        if (body == null) {
            return "no body";
        }
        if (body instanceof InputStream in && !(body instanceof StreamCache)) {
            IOHelper.close(in);
        }
        if (body instanceof Reader reader) {
            IOHelper.close(reader);
        }
        if (body instanceof CharSequence) {
            message.setBody("");
        } else if (body instanceof byte[]) {
            message.setBody(new byte[0]);
        } else if (body instanceof StreamCache) {
            message.setBody(new InputStreamCache(new byte[0]));
        } else if (body instanceof InputStream) {
            message.setBody(new ByteArrayInputStream(new byte[0]));
        } else if (body instanceof Reader) {
            message.setBody(new StringReader(""));
        } else {
            message.setBody(null);
        }
        return null;
    }

    /**
     * Removes the headers that {@code header=} names (a name, or a pattern with {@code *}, ignoring case, as header
     * names do), or without it every header that is not one of Camel's own ({@code Camel*}).
     */
    private static String removeHeaders(ChaosRule rule, Message message) {
        String pattern = rule.header != null ? rule.header.toLowerCase(Locale.ENGLISH) : null;
        List<String> names = new ArrayList<>();
        for (String name : message.getHeaders().keySet()) {
            boolean remove = pattern != null
                    ? ChaosPatterns.matches(pattern, name.toLowerCase(Locale.ENGLISH))
                    : !name.regionMatches(true, 0, "Camel", 0, 5);
            if (remove) {
                names.add(name);
            }
        }
        names.forEach(message::removeHeader);
        if (names.isEmpty()) {
            return pattern != null
                    ? "no header matches header=" + rule.header
                    : "no headers to remove (Camel* headers are left as they are)";
        }
        return null;
    }
}
