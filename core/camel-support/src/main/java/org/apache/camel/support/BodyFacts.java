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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.BaseStream;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.BodyTypeRegistry;
import org.apache.camel.util.MimeTypeHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * Says what a message body is, which step made it so, and what to write so that a step that failed on it gets the body
 * it needs: the facts a person, or a model in a dev loop, otherwise gets from a debugger (CAMEL-25749).
 * <p>
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
 * <td>text, bytes or a stream a marshal wrote from a Map or a List, as the message history says</td>
 * <td>read the field before the marshal, or marshal into a variable: an unmarshal would undo the marshal a later step
 * needs</td>
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
 * <p>
 * While the message history is on, the hint also says when other messages have reached the failing step with a body of
 * another kind (as the {@link BodyTypeRegistry} keeps them): "; other messages reached this step with data
 * (LinkedHashMap), so the body here depends on the path a message takes". Then the step is right for some messages, and
 * a path to it, such as a branch that does not unmarshal, is what differs.
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

    /** The file in the hint to read the data first, when the data format is not known. */
    private static final String DATA_FILE = "data.json";

    /** At most this many keys of a Map are listed. */
    private static final int MAX_KEYS = 20;

    /** The longest media type kept as a format: the names of real ones are short, the value comes from the message. */
    private static final int MAX_MEDIA_TYPE_LENGTH = 64;

    /** The longest Content-Type that is parsed for its format, parameters such as the charset included. */
    private static final int MAX_CONTENT_TYPE_LENGTH = 256;

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
     * data format to marshal to. For {@link Need#A_BODY}, target is the name of the data format that reads the body
     * (csv), so the file in the hint is one of its data (data.csv); it is not used for {@link Need#DATA}.
     */
    public static String hint(Exchange exchange, Need need, String target) {
        Object body = exchange.getMessage().getBody();
        String answer = switch (need) {
            case DATA -> dataHint(exchange, body);
            case BYTES -> bytesHint(body, target);
            case A_BODY -> isEmpty(body) ? readTheDataFirst(exchange, body, dataFile(target)) : null;
        };
        return answer != null ? answer + otherBodies(exchange, body) : null;
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

    /**
     * The format of a payload from its {@code Content-Type}, by the names of the catalog and of the Kamelet data types
     * (CAMEL-27468): json for application/json and the +json types, xml for application/xml, text/xml and the +xml
     * types, csv for text/csv, yaml for application/yaml, text for the other text types, and binary for
     * application/octet-stream. Any other media type is given as it is, without its parameters and in lower case.
     *
     * @param  contentType the value of the {@code Content-Type} header, or null
     * @return             the format, or null when there is no Content-Type or it is not a media type
     */
    public static String formatOf(Object contentType) {
        if (contentType == null) {
            return null;
        }
        String value = contentType.toString();
        // the ones the Kamelet data types and most components set, as they write them: no parsing for every message
        switch (value) {
            case "application/json":
                return "json";
            case "application/xml", "text/xml":
                return "xml";
            case "text/plain":
                return "text";
            case "application/octet-stream":
                return "binary";
            default:
                break;
        }
        if (value.length() > MAX_CONTENT_TYPE_LENGTH) {
            // the value comes from the message: no parsing of a long one for every node it reaches
            return null;
        }
        String type = StringHelper.before(value, ";", value).trim().toLowerCase(Locale.ROOT);
        int slash = type.indexOf('/');
        if (slash <= 0 || slash == type.length() - 1 || type.length() > MAX_MEDIA_TYPE_LENGTH || !isMediaType(type)) {
            // the value comes from the message: show only what is a media type
            return null;
        }
        String subtype = type.substring(slash + 1);
        if (subtype.equals("json") || subtype.endsWith("+json")) {
            return "json";
        } else if (subtype.equals("xml") || subtype.endsWith("+xml")) {
            return "xml";
        } else if (subtype.equals("csv")) {
            return "csv";
        } else if (subtype.equals("yaml") || subtype.equals("x-yaml") || subtype.endsWith("+yaml")) {
            return "yaml";
        } else if (type.startsWith("text/")) {
            return "text";
        } else if (type.equals("application/octet-stream")) {
            return "binary";
        }
        return type;
    }

    /**
     * Whether a type/subtype has only the characters a media type has (RFC 6838), such as no spaces or control
     * characters.
     */
    private static boolean isMediaType(String type) {
        for (int i = 0; i < type.length(); i++) {
            char ch = type.charAt(i);
            boolean ok = ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '/' || "!#$&-^_.+".indexOf(ch) >= 0;
            if (!ok) {
                return false;
            }
        }
        return type.indexOf('/') == type.lastIndexOf('/');
    }

    /**
     * A file of the data that a data format reads, for the hint: data.csv for csv; data.json when the data format is
     * not known, or reads json.
     */
    static String dataFile(String dataFormat) {
        if (dataFormat == null) {
            return DATA_FILE;
        }
        String name = dataFormat.toLowerCase(Locale.ROOT);
        if (name.contains("csv")) {
            return "data.csv";
        } else if (name.contains("tsv")) {
            return "data.tsv";
        } else if (name.contains("xml") || name.equals("jaxb")) {
            return "data.xml";
        } else if (name.contains("yaml")) {
            return "data.yaml";
        }
        return DATA_FILE;
    }

    private static String dataHint(Exchange exchange, Object body) {
        if (isEmpty(body)) {
            return readTheDataFirst(exchange, body, DATA_FILE);
        }
        if (isPayload(body)) {
            String marshal = marshalledData(exchange, body);
            return marshal != null ? marshal : unmarshalFirst(exchange, body);
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

    /**
     * When a marshal wrote the body from a Map or a List, as the message history says: the marshal is wanted, for a
     * later step that needs text (a file write), and the field is read after it, too late; an unmarshal would undo it.
     * Null when the history does not say so, as for a marshal of text, where an unmarshal was meant.
     */
    private static String marshalledData(Exchange exchange, Object body) {
        List<MessageHistory> history = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        if (history == null || history.isEmpty()) {
            return null;
        }
        String now = className(body);
        for (int i = history.size() - 1; i >= 0; i--) {
            MessageHistory step = history.get(i);
            String type = step.getBodyType();
            if (type == null || type.equals(now)) {
                continue;
            }
            // the step that set the body, and the body it was reached with
            if (step.getNode() == null || !"marshal".equals(step.getNode().getShortName())
                    || !(step instanceof DefaultMessageHistory dmh) || dmh.getBodyDataType() == null) {
                return null;
            }
            return "the " + step(step.getNode()) + " wrote it from data (" + dmh.getBodyDataType()
                   + "): read the field before the marshal, for example into a variable with setVariable, or keep the data"
                   + " in the body and marshal into a variable (marshal with variableReceive, and send that variable with"
                   + " variableSend)";
        }
        return null;
    }

    /**
     * What to write for a body that is null or empty: read the data first; or, when a setBody or a transform of the
     * route emptied it, as the message history says, read what is needed before that step. r23 (http-client s1): the
     * route set the body to ${null} and then read ${body[sku]}, and "read the data first" did not fit.
     */
    private static String readTheDataFirst(Exchange exchange, Object body, String dataFile) {
        String emptiedBy = emptiedBy(exchange, body);
        if (emptiedBy != null) {
            return "the " + emptiedBy + " emptied it: read what is needed before that step, for example into a variable"
                   + " with setVariable, or write a fixed value as a constant where it is used";
        }
        return readTheDataFirst(dataFile);
    }

    /** The setBody or transform that left the body null or empty, from the message history; or null. */
    private static String emptiedBy(Exchange exchange, Object body) {
        List<MessageHistory> history = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        if (history == null || history.isEmpty()) {
            return null;
        }
        String now = body != null ? className(body) : "null";
        for (int i = history.size() - 1; i >= 0; i--) {
            String type = history.get(i).getBodyType();
            if (type == null || type.equals(now)) {
                continue;
            }
            NamedNode node = history.get(i).getNode();
            String name = node != null ? node.getShortName() : null;
            return "setBody".equals(name) || "transform".equals(name) ? step(node) : null;
        }
        return null;
    }

    private static String readTheDataFirst(String dataFile) {
        return "read the data first - setBody with constant: resource:file:" + dataFile + " for a known file, or poll:,"
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
            return withType("a Map", type, "Map");
        } else if (body instanceof List<?>) {
            return withType("a List", type, "List");
        } else if (body instanceof Collection<?>) {
            return withType("a collection", type, "Collection");
        } else if (isIterator(body)) {
            return withType("an iterator", type, "Iterator");
        }
        return "an object of class " + body.getClass().getName();
    }

    /** The words with the class, unless the class is only the interface the words name already: a Map, for Map.of. */
    private static String withType(String words, String type, String plain) {
        return type.equals(plain) ? words : words + " (" + type + ")";
    }

    /**
     * The step that set the body, from the message history: the last node reached with a body of another type, which
     * ran on it and left this one. When every node was reached with this type, the consumer gave it; or, for an item of
     * a split, the split, as the split gives each item a message history of its own, which starts inside the split.
     * Empty when the message history is off.
     */
    private static String setBy(Exchange exchange, Object body) {
        List<MessageHistory> history = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        if (history == null || history.isEmpty()) {
            return "";
        }
        String now = body != null ? className(body) : "null";
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
        NamedNode split = splitAround(history.get(0).getNode());
        if (split != null) {
            return ", set by " + step(split);
        }
        return ", unchanged since from: " + URISupport.sanitizeUri(exchange.getFromEndpoint().getEndpointUri());
    }

    /**
     * The nearest split among the parents of a node, or null. The node itself does not count: a message that reached a
     * split had its body before the split ran on it.
     */
    private static NamedNode splitAround(NamedNode node) {
        for (NamedNode parent = node != null ? node.getParent() : null; parent != null; parent = parent.getParent()) {
            if ("split".equals(parent.getShortName())) {
                return parent;
            }
        }
        return null;
    }

    /**
     * When other messages have reached the failing step with a body of another kind, as the {@link BodyTypeRegistry}
     * knows while the message history is on: the body depends on the path a message takes, such as a branch that does
     * not unmarshal. Empty otherwise (CAMEL-25750).
     */
    private static String otherBodies(Exchange exchange, Object body) {
        List<MessageHistory> history = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        CamelContext context = exchange.getContext();
        if (history == null || history.isEmpty() || context == null) {
            return "";
        }
        // the step that failed is the last one the message reached
        MessageHistory step = history.get(history.size() - 1);
        String routeId = step.getRouteId();
        String nodeId = step.getNode() != null ? step.getNode().getId() : null;
        BodyTypeRegistry registry = context.getCamelContextExtension().getContextPlugin(BodyTypeRegistry.class);
        if (registry == null || routeId == null || nodeId == null) {
            return "";
        }
        BodyTypeRegistry.Kind kind = kindOf(body);
        StringJoiner others = new StringJoiner(" and ");
        for (BodyTypeRegistry.Kind other : registry.getBodyKinds(routeId, nodeId)) {
            if (other != kind) {
                others.add(kindWords(other, registry.getBodyTypes(routeId, nodeId, other)));
            }
        }
        if (others.length() == 0) {
            return "";
        }
        return "; other messages reached this step with " + others
               + ", so the body here depends on the path a message takes";
    }

    /** A kind in words, with the simple names of its types: data (LinkedHashMap), or no body. */
    private static String kindWords(BodyTypeRegistry.Kind kind, List<String> types) {
        String words = switch (kind) {
            case NULL -> "no body";
            case PAYLOAD -> "a payload";
            case DATA -> "data";
            case COLLECTION -> "a collection";
        };
        if (kind == BodyTypeRegistry.Kind.NULL || types.isEmpty()) {
            return words;
        }
        String names = types.stream().filter(t -> !isJdkInternal(t)).map(t -> StringHelper.afterLast(t, ".", t))
                .distinct().collect(Collectors.joining(", "));
        return names.isEmpty() ? words : words + " (" + names + ")";
    }

    /**
     * Whether a canonical class name is a class nested in a JDK class, such as java.util.ImmutableCollections.Map1 of
     * Map.of: its name says nothing to the reader, and the kind says what it is.
     */
    private static boolean isJdkInternal(String type) {
        if (!type.startsWith("java.")) {
            return false;
        }
        String outer = StringHelper.beforeLast(type, ".", "");
        String outerName = StringHelper.afterLast(outer, ".", outer);
        return !outerName.isEmpty() && Character.isUpperCase(outerName.charAt(0));
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

    /**
     * The kind of the body, for what a step can do with it: the payload as it is (text, bytes, a stream or a file, or a
     * single value such as a number, which converts to text), data (a Map or an object), a collection (a List, a Set or
     * an array, or an iterator or a stream over them), or null.
     *
     * @param  body the message body
     * @return      the kind of the body
     */
    public static BodyTypeRegistry.Kind kindOf(Object body) {
        if (body == null) {
            return BodyTypeRegistry.Kind.NULL;
        } else if (isPayload(body) || isValue(body)) {
            return BodyTypeRegistry.Kind.PAYLOAD;
        } else if (body instanceof Map<?, ?>) {
            // a Map that is also Iterable (such as a SimpleOrderedMap) is data, as describe calls it
            return BodyTypeRegistry.Kind.DATA;
        } else if (body instanceof Collection<?> || body.getClass().isArray() || isIterator(body)) {
            return BodyTypeRegistry.Kind.COLLECTION;
        }
        return BodyTypeRegistry.Kind.DATA;
    }

    /**
     * Whether the text a type converter gave for a value is no more than the toString() of the value, which is what
     * ToStringTypeConverter, the fallback that is never promoted, gives for an object no other converter reads as text,
     * such as a Map or a POJO. Asking the type converter for its converter does not tell them apart, as the registry
     * caches that fallback for the pair of types after the first conversion, so a step that reads a value as text
     * compares the text instead, and fails rather than send the toString() of an object.
     * <p>
     * A payload (text, bytes, a stream, a file) or a single value (a number, a boolean, an enum) is text by itself,
     * whatever its toString(), and so is an id, an address or a point in time (a UUID, URI, URL, java.time value or
     * Date), whose toString() is its text and which no type converter reads otherwise. A value with a real converter to
     * text whose text happens to equal its toString() counts as only a toString() too: a step that rejects it should
     * name convertBodyTo(String.class) as the way to send that text.
     *
     * @param  value the value, not null
     * @param  text  the text the type converter gave for the value
     * @return       true when the text is no more than value.toString()
     */
    public static boolean isOnlyToString(Object value, String text) {
        return kindOf(value) != BodyTypeRegistry.Kind.PAYLOAD && !isTextValue(value) && text.equals(value.toString());
    }

    /** A value whose toString() is its text, rather than a dump of its fields: an id, an address or a point in time. */
    private static boolean isTextValue(Object value) {
        return value instanceof UUID || value instanceof URI || value instanceof URL || value instanceof Temporal
                || value instanceof TemporalAmount || value instanceof Date;
    }

    /**
     * The payload in its serialized form: text, bytes, a stream or a file. A ByteArrayOutputStream, which camel-tika
     * parse puts in the body, holds the bytes written to it, and converts to bytes, a stream or text as they do.
     */
    private static boolean isPayload(Object body) {
        return body instanceof String || body instanceof byte[] || body instanceof ByteArrayOutputStream
                || body instanceof InputStream || body instanceof Reader
                || body instanceof StreamCache || body instanceof WrappedFile<?> || body instanceof File
                || body instanceof Path;
    }

    /**
     * A single value, which a type converter turns into text: it has no fields to read, and writing it needs no
     * marshal.
     */
    private static boolean isValue(Object body) {
        return body instanceof CharSequence || body instanceof Number || body instanceof Boolean
                || body instanceof Character || body instanceof Enum<?>;
    }

    private static boolean isIterator(Object body) {
        return body instanceof Iterator<?> || body instanceof BaseStream<?, ?>
                || body instanceof Iterable<?> && !(body instanceof Collection<?>) && !(body instanceof Path);
    }

    private static boolean isEmpty(Object body) {
        return body == null || body instanceof String s && s.isBlank() || body instanceof byte[] b && b.length == 0;
    }

    /**
     * The class name of the body as the message history keeps it: the canonical name, or the class name when the class
     * has none (an anonymous, local or hidden class, such as a lambda). Null for a null body.
     */
    static String className(Object body) {
        if (body == null) {
            return null;
        }
        Class<?> type = body.getClass();
        String name = type.getCanonicalName();
        return name != null ? name : type.getName();
    }

    /**
     * The class of a body for a message: its simple name, or for a class the JDK keeps to itself (the Map of Map.of,
     * the List of Arrays.asList) the interface it implements, as its own name says nothing to the reader.
     *
     * @param  body the body, not null
     * @return      the name, such as LinkedHashMap, or Map for the Map of Map.of
     */
    public static String typeName(Object body) {
        Class<?> type = body.getClass();
        if (!Modifier.isPublic(type.getModifiers()) && type.getName().startsWith("java.")) {
            if (body instanceof Map<?, ?>) {
                return "Map";
            } else if (body instanceof List<?>) {
                return "List";
            } else if (body instanceof Set<?>) {
                return "Set";
            } else if (body instanceof Collection<?>) {
                return "Collection";
            } else if (body instanceof Iterator<?>) {
                return "Iterator";
            }
        }
        String name = type.getSimpleName();
        return name.isEmpty() ? type.getName() : name;
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
