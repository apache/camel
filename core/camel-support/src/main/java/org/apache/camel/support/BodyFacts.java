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
package org.apache.camel.support;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.BaseStream;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.MimeTypeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * Says what a message body is, which step made it so, and what to write so that a step that failed on it gets the body
 * it needs: the facts a person, or a model in a dev loop, otherwise gets from a debugger (CAMEL-25749).
 * <p/>
 * A failure on the body is told in two parts: {@link #describe(Exchange)} says what the body is and, when the message
 * history is on, the step that set it; {@link #hint(Exchange, Need)} says what to write, for what the failing step
 * needed. A caller puts them after its own words:
 *
 * <pre>
 * ${body[status]} failed: the body is the file as read (a GenericFile), unchanged since from: file://orders:
 * unmarshal it first (unmarshal: json, for the file name order-1003.json) to read its fields
 * </pre>
 *
 * Only a failure about the body gets a body sentence: an error about a header or an exchange property gets none.
 *
 * <table>
 * <caption>The hint, by what the step needs and what the body is</caption>
 * <tr>
 * <th>Need</th>
 * <th>The body is</th>
 * <th>The hint</th>
 * </tr>
 * <tr>
 * <td>{@link Need#DATA}</td>
 * <td>text, bytes, a stream, the file as read</td>
 * <td>unmarshal it first, in the data format the Content-Type, the file name or the text names</td>
 * </tr>
 * <tr>
 * <td>{@link Need#DATA}</td>
 * <td>null, or empty text or bytes</td>
 * <td>as for {@link Need#A_BODY}</td>
 * </tr>
 * <tr>
 * <td>{@link Need#DATA}</td>
 * <td>a Map</td>
 * <td>its keys</td>
 * </tr>
 * <tr>
 * <td>{@link Need#DATA}</td>
 * <td>an iterator</td>
 * <td>split it, or have the producer give a List</td>
 * </tr>
 * <tr>
 * <td>{@link Need#DATA}</td>
 * <td>a List, or an object</td>
 * <td>none: the name read is wrong, not the body</td>
 * </tr>
 * <tr>
 * <td>{@link Need#BYTES}</td>
 * <td>a Map, a List or an object</td>
 * <td>marshal it first, in the data format the Content-Type or the name of the file being written names, or set the
 * body to the text</td>
 * </tr>
 * <tr>
 * <td>{@link Need#BYTES}</td>
 * <td>an iterator</td>
 * <td>split it, or convert it first</td>
 * </tr>
 * <tr>
 * <td>{@link Need#BYTES}</td>
 * <td>null, or already text, bytes, a stream or a file</td>
 * <td>none: that is not this failure</td>
 * </tr>
 * <tr>
 * <td>{@link Need#A_BODY}</td>
 * <td>null, or empty text or bytes</td>
 * <td>read the data first - setBody with constant: resource:file:..., poll:, pollEnrich or a from: consumer</td>
 * </tr>
 * </table>
 * When the Content-Type, the file name or the text names no data format, the hint lists the usual ones (json,
 * jacksonXml, csv). A hint is null when the body is not why the step failed.
 *
 * @since 4.24
 */
public final class BodyFacts {

    /** What a step that failed on the body needed the body to be. */
    public enum Need {

        /**
         * The step reads fields of the body: {@code ${body[sku]}}, {@code ${body.sku}}, Groovy {@code body.sku} or
         * {@code body.find { ... }}, bean OGNL. So the body must be data: a Map (its keys), a List (its elements) or an
         * object (its properties, by getter or field). Not the payload as text or bytes, which an unmarshal turns into
         * data. A jsonpath expression is not such a read: it reads the fields of JSON text as well.
         */
        DATA,

        /**
         * The step writes or sends the payload: an HTTP request or reply, a file. So the body must be in its serialized
         * form: text, a byte[], a stream or a file. Binary payloads count, not only characters. Not a Map, a List or an
         * object, which a marshal turns into text.
         */
        BYTES,

        /**
         * The step needs a body at all, of any type: unmarshal, marshal, jsonpath, jq, xslt. So the body must not be
         * null, nor empty text or bytes.
         */
        A_BODY
    }

    /** At most this many keys of a Map are listed. */
    private static final int MAX_KEYS = 20;

    /** The usual data formats, named when nothing names the one of the payload. */
    private static final String USUAL_UNMARSHAL = "unmarshal: json, unmarshal: jacksonXml or unmarshal: csv";
    private static final String USUAL_MARSHAL = "marshal: json, marshal: jacksonXml or marshal: csv";

    private BodyFacts() {
    }

    /**
     * What the body is, in plain words, and the step that set it when the message history is on: "the body is a Map
     * (LinkedHashMap), set by unmarshal at orders.yaml:9", or "the body is null, unchanged since from: timer://tick".
     */
    public static String describe(Exchange exchange) {
        Object body = exchange.getMessage().getBody();
        return "the body is " + words(body) + setBy(exchange, body);
    }

    /** What to write so that the failing step gets the body it needs, for {@link Need#DATA} and {@link Need#A_BODY}. */
    public static String hint(Exchange exchange, Need need) {
        return hint(exchange, need, null);
    }

    /**
     * What to write so that the failing step gets the body it needs; null when the body is not why it failed. For
     * {@link Need#BYTES}, target is what the step writes: its Content-Type, or the name of the file, which names the
     * data format to marshal to; it is not used for the other needs.
     */
    public static String hint(Exchange exchange, Need need, String target) {
        Object body = exchange.getMessage().getBody();
        return switch (need) {
            case DATA -> dataHint(exchange, body);
            case BYTES -> bytesHint(body, target);
            case A_BODY -> isEmpty(body) ? readTheDataFirst() : null;
        };
    }

    /**
     * The data format that reads and writes a content type, as the YAML DSL names it: json, jacksonXml, csv (tab
     * separated values too) or yaml; or null.
     */
    public static String dataFormatOf(String contentType) {
        if (contentType == null) {
            return null;
        }
        String type = StringHelper.before(contentType, ";", contentType).trim().toLowerCase(Locale.ROOT);
        if (type.endsWith("/json") || type.endsWith("+json")) {
            return "json";
        } else if (type.endsWith("/xml") || type.endsWith("+xml")) {
            return "jacksonXml";
        } else if (type.endsWith("/csv") || type.endsWith("/tab-separated-values")) {
            return "csv";
        } else if (type.endsWith("/yaml") || type.endsWith("/x-yaml")) {
            return "yaml";
        }
        return null;
    }

    private static String dataHint(Exchange exchange, Object body) {
        if (isEmpty(body)) {
            return readTheDataFirst();
        }
        if (isPayload(body)) {
            return unmarshalFirst(exchange, body);
        }
        if (body instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "it is empty";
            }
            String keys = map.keySet().stream().limit(MAX_KEYS).map(String::valueOf).collect(Collectors.joining(", "));
            return "its keys are: " + keys + (map.size() > MAX_KEYS ? ", ... (" + map.size() + " keys)" : "");
        }
        if (isIterator(body)) {
            return "it has no size and can be read once: split it, or have the producer give a List";
        }
        return null;
    }

    private static String bytesHint(Object body, String target) {
        if (body == null || isPayload(body)) {
            return null;
        }
        if (isIterator(body)) {
            return "split it, or convert it first";
        }
        String format = dataFormatOf(target);
        String named = format != null ? "for the Content-Type " + target : null;
        if (format == null && target != null) {
            format = dataFormatOf(MimeTypeHelper.probeMimeType(target));
            named = format != null ? "for the file name " + target : null;
        }
        return (format != null
                ? "marshal it first (marshal: " + format + ", " + named + ")"
                : "marshal it first with the data format to write (such as " + USUAL_MARSHAL + ")")
               + ", or set the body to the text";
    }

    /**
     * Unmarshal the payload, in the data format the Content-Type, the name of the file it was read from, or the first
     * character of the text names.
     */
    private static String unmarshalFirst(Exchange exchange, Object body) {
        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        String fileName = exchange.getMessage().getHeader(Exchange.FILE_NAME, String.class);
        String format = dataFormatOf(contentType);
        String named = format != null ? ", for the Content-Type " + contentType : "";
        if (format == null && fileName != null) {
            format = dataFormatOf(MimeTypeHelper.probeMimeType(fileName));
            named = format != null ? ", for the file name " + fileName : "";
        }
        if (format == null) {
            int first = firstNonBlank(body);
            format = first == '{' || first == '[' ? "json" : first == '<' ? "jacksonXml" : null;
        }
        if (format == null) {
            return "unmarshal it first with the data format of the payload (such as " + USUAL_UNMARSHAL
                   + ") to read its fields";
        }
        String unmarshal = "unmarshal it first (unmarshal: " + format + named + ")";
        return switch (format) {
            case "jacksonXml" -> unmarshal + " to read its fields, or read them with xpath";
            case "csv" -> unmarshal + " to read its rows: a List of rows, or a Map per row with useMaps: true";
            default -> unmarshal + " to read its fields";
        };
    }

    private static String readTheDataFirst() {
        return "read the data first - setBody with constant: resource:file:data.json for a known file, or poll:,"
               + " pollEnrich or a from: consumer for one that is not (a route reached with direct: has the body of its"
               + " caller)";
    }

    /** The body in plain words, with its class. */
    static String words(Object body) {
        if (body == null) {
            return "null";
        }
        String type = typeName(body);
        if (body instanceof String s) {
            return (s.isBlank() ? "empty text" : "text") + " (a " + type + ")";
        } else if (body instanceof byte[] b) {
            return (b.length == 0 ? "empty bytes" : "bytes") + " (a " + type + ")";
        } else if (body instanceof WrappedFile<?>) {
            return "the file as read (a " + type + ")";
        } else if (body instanceof File || body instanceof Path) {
            return "a file (a " + type + ")";
        } else if (body instanceof StreamCache) {
            return "a cached stream (" + type + ")";
        } else if (body instanceof InputStream || body instanceof Reader) {
            return "a stream (" + type + ")";
        } else if (body instanceof Map<?, ?>) {
            return "a Map (" + type + ")";
        } else if (body instanceof List<?>) {
            return "a List (" + type + ")";
        } else if (body instanceof Collection<?>) {
            return "a collection (" + type + ")";
        } else if (isIterator(body)) {
            return "an iterator (" + type + ")";
        }
        return "an object of class " + body.getClass().getName();
    }

    /**
     * The step that set the body, from the message history: the last node reached with a body of another type, which
     * ran on it and left this one. When every node was reached with this type, the consumer gave it. Empty when the
     * message history is off.
     */
    private static String setBy(Exchange exchange, Object body) {
        List<MessageHistory> history = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        if (history == null || history.isEmpty()) {
            return "";
        }
        String now = body != null ? ObjectHelper.classCanonicalName(body) : "null";
        boolean recorded = false;
        for (int i = history.size() - 1; i >= 0; i--) {
            String type = history.get(i).getBodyType();
            if (type == null) {
                continue;
            }
            recorded = true;
            if (!type.equals(now)) {
                return ", set by " + step(history.get(i).getNode());
            }
        }
        if (!recorded || exchange.getFromEndpoint() == null) {
            return "";
        }
        return ", unchanged since from: " + URISupport.sanitizeUri(exchange.getFromEndpoint().getEndpointUri());
    }

    /** A step as people find it: unmarshal at orders.yaml:9, or the step and its label when there is no location. */
    private static String step(NamedNode node) {
        String location = LoggerHelper.getLineNumberLoggerName(node);
        if (location != null) {
            return node.getShortName() + " at " + location;
        }
        String label = node.getLabel();
        return label != null && !label.isBlank()
                ? node.getShortName() + " (" + StringHelper.limitLength(URISupport.sanitizeUri(label), 60) + ")"
                : node.getShortName();
    }

    /** The payload in its serialized form: text, bytes, a stream or a file. */
    private static boolean isPayload(Object body) {
        return body instanceof String || body instanceof byte[] || body instanceof InputStream || body instanceof Reader
                || body instanceof StreamCache || body instanceof WrappedFile<?> || body instanceof File
                || body instanceof Path;
    }

    private static boolean isIterator(Object body) {
        return body instanceof Iterator<?> || body instanceof BaseStream<?, ?>
                || body instanceof Iterable<?> && !(body instanceof Collection<?>) && !(body instanceof Path);
    }

    private static boolean isEmpty(Object body) {
        return body == null || body instanceof String s && s.isBlank() || body instanceof byte[] b && b.length == 0;
    }

    private static String typeName(Object body) {
        String name = body.getClass().getSimpleName();
        return name.isEmpty() ? body.getClass().getName() : name;
    }

    /** The first character of text that is not blank, or -1: only text in memory is looked at, never a stream. */
    private static int firstNonBlank(Object body) {
        String text = body instanceof String s ? s
                : body instanceof byte[] b ? new String(b, 0, Math.min(b.length, 64), StandardCharsets.UTF_8) : null;
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                if (!Character.isWhitespace(text.charAt(i))) {
                    return text.charAt(i);
                }
            }
        }
        return -1;
    }
}
