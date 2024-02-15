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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.Route;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.trait.message.MessageTrait;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Some helper methods when working with {@link org.apache.camel.Message}.
 */
public final class MessageHelper {

    private static final String MESSAGE_HISTORY_HEADER = "%-40s %-30s %-50s %-12s";
    private static final String MESSAGE_HISTORY_OUTPUT = "%-40.40s %-30.30s %-50.50s %12.12s";

    /**
     * Utility classes should not have a public constructor.
     */
    private MessageHelper() {
    }

    /**
     * Extracts the given body and returns it as a String, that can be used for logging etc.
     * <p/>
     * Will handle stream based bodies wrapped in StreamCache.
     *
     * @param  message the message with the body
     * @return         the body as String, can return <tt>null</null> if no body
     */
    public static String extractBodyAsString(Message message) {
        if (message == null) {
            return null;
        }

        // optimize if the body is a String type already
        Object body = message.getBody();
        if (body instanceof String) {
            return (String) body;
        }

        // we need to favor using stream cache so the body can be re-read later
        StreamCache newBody = message.getExchange().getContext().getTypeConverter().tryConvertTo(StreamCache.class,
                message.getExchange(), body);
        if (newBody != null) {
            message.setBody(newBody);
        }

        Object answer = message.getBody(String.class);
        if (answer == null) {
            answer = message.getBody();
        }

        if (newBody != null) {
            // Reset the InputStreamCache
            newBody.reset();
        }

        return answer != null ? answer.toString() : null;
    }

    /**
     * Gets the given body class type name as a String.
     * <p/>
     * Will skip java.lang. for the build in Java types.
     *
     * @param  message the message with the body
     * @return         the body type name as String, can return <tt>null</null> if no body
     */
    public static String getBodyTypeName(Message message) {
        if (message == null) {
            return null;
        }
        String answer = ObjectHelper.classCanonicalName(message.getBody());
        if (answer != null && answer.startsWith("java.lang.")) {
            return answer.substring(10);
        }
        return answer;
    }

    /**
     * If the message body contains a {@link StreamCache} instance, reset the cache to enable reading from it again.
     *
     * @param message the message for which to reset the body
     */
    public static void resetStreamCache(Message message) {
        if (message == null) {
            return;
        }
        Object body = null;
        try {
            body = message.getBody();
        } catch (Exception e) {
            // ignore
        }
        if (body instanceof StreamCache) {
            ((StreamCache) body).reset();
        }
    }

    /**
     * Returns the MIME content type on the message or <tt>null</tt> if none defined
     */
    public static String getContentType(Message message) {
        return message.getHeader(Exchange.CONTENT_TYPE, String.class);
    }

    /**
     * Returns the MIME content encoding on the message or <tt>null</tt> if none defined
     */
    public static String getContentEncoding(Message message) {
        return message.getHeader(Exchange.CONTENT_ENCODING, String.class);
    }

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging. Will prepend the message with <tt>Message: </tt>
     *
     * @param  message the message
     * @return         the logging message
     * @see            org.apache.camel.Exchange#LOG_DEBUG_BODY_STREAMS
     * @see            org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     */
    public static String extractBodyForLogging(Message message) {
        return extractBodyForLogging(message, "Message: ");
    }

    /**
     * Extracts the value for logging purpose.
     * <p/>
     * Will clip the value if its too big for logging.
     *
     * @param  value   the value
     * @param  message the message
     * @return         the logging message
     * @see            org.apache.camel.Exchange#LOG_DEBUG_BODY_STREAMS
     * @see            org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     */
    public static String extractValueForLogging(Object value, Message message) {
        boolean streams = isStreams(message);

        // default to 1000 chars
        int maxChars = 1000;

        if (message.getExchange() != null) {
            String property = message.getExchange().getContext().getGlobalOption(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
            if (property != null) {
                maxChars = message.getExchange().getContext().getTypeConverter().convertTo(Integer.class, property);
            }
        }

        return extractValueForLogging(value, message, streams, false, maxChars);
    }

    private static boolean isStreams(Message message) {
        boolean streams = false;
        if (message.getExchange() != null) {
            String globalOption = message.getExchange().getContext().getGlobalOption(Exchange.LOG_DEBUG_BODY_STREAMS);
            if (globalOption != null) {
                streams = message.getExchange().getContext().getTypeConverter().convertTo(Boolean.class, message.getExchange(),
                        globalOption);
            }
        }
        return streams;
    }

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging.
     *
     * @param  message the message
     * @param  prepend a message to prepend
     * @return         the logging message
     * @see            org.apache.camel.Exchange#LOG_DEBUG_BODY_STREAMS
     * @see            org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     */
    public static String extractBodyForLogging(Message message, String prepend) {
        boolean streams = isStreams(message);
        return extractBodyForLogging(message, prepend, streams, false);
    }

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging.
     *
     * @param  message      the message
     * @param  prepend      a message to prepend
     * @param  allowStreams whether or not streams is allowed
     * @param  allowFiles   whether or not files is allowed (currently not in use)
     * @return              the logging message
     * @see                 org.apache.camel.Exchange#LOG_DEBUG_BODY_STREAMS
     * @see                 org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     */
    public static String extractBodyForLogging(Message message, String prepend, boolean allowStreams, boolean allowFiles) {
        // default to 1000 chars
        int maxChars = 1000;

        if (message.getExchange() != null) {
            String globalOption = message.getExchange().getContext().getGlobalOption(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
            if (globalOption != null) {
                maxChars = message.getExchange().getContext().getTypeConverter().convertTo(Integer.class, globalOption);
            }
        }

        return extractBodyForLogging(message, prepend, allowStreams, allowFiles, maxChars);
    }

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging.
     *
     * @param  message      the message
     * @param  prepend      a message to prepend (optional)
     * @param  allowStreams whether or not streams is allowed
     * @param  allowFiles   whether or not files is allowed (currently not in use)
     * @param  maxChars     limit to maximum number of chars. Use 0 for not limit, and -1 for turning logging message
     *                      body off.
     * @return              the logging message
     * @see                 org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     */
    public static String extractBodyForLogging(
            Message message, String prepend, boolean allowStreams, boolean allowFiles, int maxChars) {
        String value = extractValueForLogging(message.getBody(), message, allowStreams, allowFiles, maxChars);
        if (prepend != null) {
            return prepend + value;
        } else {
            return value;
        }
    }

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging.
     *
     * @param  message            the message
     * @param  prepend            a message to prepend (optional)
     * @param  allowCachedStreams whether or not cached streams is allowed
     * @param  allowStreams       whether or not streams is allowed
     * @param  allowFiles         whether or not files is allowed (currently not in use)
     * @param  maxChars           limit to maximum number of chars. Use 0 for not limit, and -1 for turning logging
     *                            message body off.
     * @return                    the logging message
     * @see                       org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     */
    public static String extractBodyForLogging(
            Message message, String prepend, boolean allowCachedStreams, boolean allowStreams, boolean allowFiles,
            int maxChars) {
        String value
                = extractValueForLogging(message.getBody(), message, allowCachedStreams, allowStreams, allowFiles, maxChars);
        if (prepend != null) {
            return prepend + value;
        } else {
            return value;
        }
    }

    /**
     * Extracts the value for logging purpose.
     * <p/>
     * Will clip the value if its too big for logging.
     *
     * @param  obj          the value
     * @param  message      the message
     * @param  allowStreams whether or not streams is allowed
     * @param  allowFiles   whether or not files is allowed (currently not in use)
     * @param  maxChars     limit to maximum number of chars. Use 0 for not limit, and -1 for turning logging message
     *                      body off.
     * @return              the logging message
     * @see                 org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     */
    public static String extractValueForLogging(
            Object obj, Message message, boolean allowStreams, boolean allowFiles, int maxChars) {
        return extractValueForLogging(obj, message, allowStreams, allowStreams, allowFiles, maxChars);

    }

    /**
     * Extracts the value for logging purpose.
     * <p/>
     * Will clip the value if its too big for logging.
     *
     * @param  obj                the value
     * @param  message            the message
     * @param  allowCachedStreams whether or not cached streams is allowed
     * @param  allowStreams       whether or not streams is allowed
     * @param  allowFiles         whether or not files is allowed (currently not in use)
     * @param  maxChars           limit to maximum number of chars. Use 0 for not limit, and -1 for turning logging
     *                            message body off.
     * @return                    the logging message
     * @see                       org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     */
    public static String extractValueForLogging(
            Object obj, Message message, boolean allowCachedStreams, boolean allowStreams, boolean allowFiles, int maxChars) {
        if (maxChars < 0) {
            return "[Body is not logged]";
        }

        if (obj == null) {
            return "[Body is null]";
        }

        if (!allowFiles) {
            if (obj instanceof WrappedFile || obj instanceof File) {
                return "[Body is file based: " + obj + "]";
            }
        }

        if (!allowStreams) {
            boolean allow = allowCachedStreams && obj instanceof StreamCache;
            if (!allow) {
                if (obj instanceof StreamCache) {
                    return "[Body is instance of org.apache.camel.StreamCache]";
                } else if (obj instanceof InputStream) {
                    return "[Body is instance of java.io.InputStream]";
                } else if (obj instanceof OutputStream) {
                    return "[Body is instance of java.io.OutputStream]";
                } else if (obj instanceof Reader) {
                    return "[Body is instance of java.io.Reader]";
                } else if (obj instanceof Writer) {
                    return "[Body is instance of java.io.Writer]";
                } else if (obj.getClass().getName().equals("javax.xml.transform.stax.StAXSource")) {
                    // StAX source is streaming based
                    return "[Body is instance of javax.xml.transform.Source]";
                }
            }
        }

        // is the body a stream cache or input stream
        StreamCache cache = null;
        InputStream is = null;
        if (obj instanceof StreamCache) {
            cache = (StreamCache) obj;
        } else if (obj instanceof InputStream) {
            is = (InputStream) obj;
        }

        // grab the message body as a string
        String body = null;
        if (message.getExchange() != null) {
            try {
                body = message.getExchange().getContext().getTypeConverter().tryConvertTo(String.class, message.getExchange(),
                        obj);
            } catch (Exception e) {
                // ignore as the body is for logging purpose
            }
        }
        if (body == null) {
            try {
                body = obj.toString();
            } catch (Exception e) {
                // ignore as the body is for logging purpose
            }
        }

        // reset stream cache after use
        if (cache != null) {
            cache.reset();
        } else if (is != null && is.markSupported()) {
            try {
                is.reset();
            } catch (IOException e) {
                // ignore
            }
        }

        if (body == null) {
            return "[Body is null]";
        }

        // clip body if length enabled and the body is too big
        if (maxChars > 0 && body.length() > maxChars) {
            body = body.substring(0, maxChars) + "... [Body clipped after " + maxChars + " chars, total length is "
                   + body.length() + "]";
        }

        return body;
    }

    /**
     * Dumps the message as a generic XML structure.
     *
     * @param  message the message
     * @return         the XML
     */
    public static String dumpAsXml(Message message) {
        return dumpAsXml(message, true);
    }

    /**
     * Dumps the message as a generic XML structure.
     *
     * @param  message     the message
     * @param  includeBody whether or not to include the message body
     * @return             the XML
     */
    public static String dumpAsXml(Message message, boolean includeBody) {
        return dumpAsXml(message, includeBody, 0);
    }

    /**
     * Dumps the message as a generic XML structure.
     *
     * @param  message     the message
     * @param  includeBody whether or not to include the message body
     * @param  indent      number of spaces to indent
     * @return             the XML
     */
    public static String dumpAsXml(Message message, boolean includeBody, int indent) {
        return dumpAsXml(message, includeBody, indent, false, true, 128 * 1024);
    }

    /**
     * Dumps the message as a generic XML structure.
     *
     * @param  message      the message
     * @param  includeBody  whether or not to include the message body
     * @param  indent       number of spaces to indent
     * @param  allowStreams whether to include message body if they are stream based
     * @param  allowFiles   whether to include message body if they are file based
     * @param  maxChars     clip body after maximum chars (to avoid very big messages). Use 0 or negative value to not
     *                      limit at all.
     * @return              the XML
     */
    public static String dumpAsXml(
            Message message, boolean includeBody, int indent, boolean allowStreams, boolean allowFiles, int maxChars) {
        return dumpAsXml(message, false, false, includeBody, indent, allowStreams, allowStreams, allowFiles, maxChars);
    }

    /**
     * Dumps the message as a generic XML structure.
     *
     * @param  message                   the message
     * @param  includeExchangeProperties whether or not to include exchange properties
     * @param  includeExchangeVariables  whether or not to include exchange variables
     * @param  includeBody               whether or not to include the message body
     * @param  indent                    number of spaces to indent
     * @param  allowCachedStreams        whether to include message body if they are stream cache based
     * @param  allowStreams              whether to include message body if they are stream based
     * @param  allowFiles                whether to include message body if they are file based
     * @param  maxChars                  clip body after maximum chars (to avoid very big messages). Use 0 or negative
     *                                   value to not limit at all.
     * @return                           the XML
     */
    public static String dumpAsXml(
            Message message, boolean includeExchangeProperties, boolean includeExchangeVariables,
            boolean includeBody, int indent, boolean allowCachedStreams, boolean allowStreams,
            boolean allowFiles, int maxChars) {
        StringBuilder sb = new StringBuilder();

        StringBuilder prefix = new StringBuilder();
        prefix.append(" ".repeat(indent));

        // include exchangeId/exchangePattern/type as attribute on the <message> tag
        sb.append(prefix);
        String messageType = ObjectHelper.classCanonicalName(message);
        String exchangeType = ObjectHelper.classCanonicalName(message.getExchange());
        sb.append("<message exchangeId=\"").append(message.getExchange().getExchangeId())
                .append("\" exchangePattern=\"").append(message.getExchange().getPattern().name())
                .append("\" exchangeType=\"").append(exchangeType)
                .append("\" messageType=\"").append(messageType).append("\">\n");

        // exchange variables
        if (includeExchangeVariables && message.getExchange().hasVariables()) {
            sb.append(prefix);
            sb.append("  <exchangeVariables>\n");
            // sort the exchange variables so they are listed A..Z
            Map<String, Object> variables = new TreeMap<>(message.getExchange().getVariables());
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String type = ObjectHelper.classCanonicalName(value);
                sb.append(prefix);
                sb.append("    <exchangeVariable key=\"").append(key).append("\"");
                if (type != null) {
                    sb.append(" type=\"").append(type).append("\"");
                }
                sb.append(">");

                // dump value as XML, use Camel type converter to convert to String
                if (value != null) {
                    try {
                        String xml = extractValueForLogging(value, message, allowCachedStreams, allowStreams, allowFiles,
                                maxChars);
                        if (xml != null) {
                            // must always xml encode
                            sb.append(StringHelper.xmlEncode(xml));
                        }
                    } catch (Exception e) {
                        // ignore as the body is for logging purpose
                    }
                }
                sb.append("</exchangeVariable>\n");
            }
            sb.append(prefix);
            sb.append("  </exchangeVariables>\n");
        }
        // exchange properties
        if (includeExchangeProperties && message.getExchange().hasProperties()) {
            sb.append(prefix);
            sb.append("  <exchangeProperties>\n");
            // sort the exchange properties so they are listed A..Z
            Map<String, Object> properties = new TreeMap<>(message.getExchange().getAllProperties());
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                // skip message history
                if (Exchange.MESSAGE_HISTORY.equals(key)) {
                    continue;
                }
                Object value = entry.getValue();
                String type = ObjectHelper.classCanonicalName(value);
                sb.append(prefix);
                sb.append("    <exchangeProperty key=\"").append(key).append("\"");
                if (type != null) {
                    sb.append(" type=\"").append(type).append("\"");
                }
                sb.append(">");

                // dump value as XML, use Camel type converter to convert to String
                if (value != null) {
                    try {
                        String xml = extractValueForLogging(value, message, allowCachedStreams, allowStreams, allowFiles,
                                maxChars);
                        if (xml != null) {
                            // must always xml encode
                            sb.append(StringHelper.xmlEncode(xml));
                        }
                    } catch (Exception e) {
                        // ignore as the body is for logging purpose
                    }
                }
                sb.append("</exchangeProperty>\n");
            }
            sb.append(prefix);
            sb.append("  </exchangeProperties>\n");
        }
        // headers
        if (message.hasHeaders()) {
            sb.append(prefix);
            sb.append("  <headers>\n");
            // sort the headers so they are listed A..Z
            Map<String, Object> headers = new TreeMap<>(message.getHeaders());
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                Object value = entry.getValue();
                String type = ObjectHelper.classCanonicalName(value);
                sb.append(prefix);
                sb.append("    <header key=\"").append(entry.getKey()).append("\"");
                if (type != null) {
                    sb.append(" type=\"").append(type).append("\"");
                }
                sb.append(">");

                // dump value as XML, use Camel type converter to convert to String
                if (value != null) {
                    try {
                        String xml = extractValueForLogging(value, message, allowCachedStreams, allowStreams, allowFiles,
                                maxChars);
                        if (xml != null) {
                            // must always xml encode
                            sb.append(StringHelper.xmlEncode(xml));
                        }
                    } catch (Exception e) {
                        // ignore as the body is for logging purpose
                    }
                }
                sb.append("</header>\n");
            }
            sb.append(prefix);
            sb.append("  </headers>\n");
        }
        if (includeBody) {
            sb.append(prefix);
            sb.append("  <body");
            Object body = message.getBody();
            String type = ObjectHelper.classCanonicalName(body);
            if (type != null) {
                sb.append(" type=\"").append(type).append("\"");
            }
            if (body instanceof Collection) {
                long size = ((Collection<?>) body).size();
                sb.append(" size=\"").append(size).append("\"");
            }
            if (body != null && body.getClass().isArray()) {
                int size = Array.getLength(body);
                sb.append(" size=\"").append(size).append("\"");
            }
            if (body instanceof StreamCache) {
                long pos = ((StreamCache) body).position();
                if (pos != -1) {
                    sb.append(" position=\"").append(pos).append("\"");
                }
            }
            sb.append(">");

            String xml = extractBodyForLogging(message, null, allowCachedStreams, allowStreams, allowFiles, maxChars);
            if (xml != null) {
                // must always xml encode
                sb.append(StringHelper.xmlEncode(xml));
            }

            sb.append("</body>\n");
        }

        sb.append(prefix);
        sb.append("</message>");
        return sb.toString();
    }

    /**
     * Copies the body of the source message to the body of the target message while preserving the data type if the
     * messages are both of type {@link DataTypeAware}. .
     *
     * @param source the source message from which the body must be extracted.
     * @param target the target message that will receive the body.
     */
    public static void copyBody(Message source, Message target) {
        // Preserve the DataType if both messages are DataTypeAware
        if (source.hasTrait(MessageTrait.DATA_AWARE)) {
            target.setBody(source.getBody());
            target.setPayloadForTrait(MessageTrait.DATA_AWARE,
                    source.getPayloadForTrait(MessageTrait.DATA_AWARE));

            return;
        }

        target.setBody(source.getBody());
    }

    /**
     * Copies the headers from the source to the target message.
     *
     * @param source   the source message
     * @param target   the target message
     * @param override whether to override existing headers
     */
    public static void copyHeaders(Message source, Message target, boolean override) {
        copyHeaders(source, target, null, override);
    }

    /**
     * Copies the headers from the source to the target message.
     *
     * @param source   the source message
     * @param target   the target message
     * @param strategy the header filter strategy which could help us to filter the protocol message headers
     * @param override whether to override existing headers
     */
    public static void copyHeaders(Message source, Message target, HeaderFilterStrategy strategy, boolean override) {
        if (!source.hasHeaders()) {
            return;
        }

        for (Map.Entry<String, Object> entry : source.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (target.getHeader(key) == null || override) {
                if (strategy == null) {
                    target.setHeader(key, value);
                } else if (!strategy.applyFilterToExternalHeaders(key, value, target.getExchange())) {
                    // Just make sure we don't copy the protocol headers to
                    // target
                    target.setHeader(key, value);
                }
            }
        }
    }

    /**
     * Dumps the {@link MessageHistory} from the {@link Exchange} in a human readable format.
     *
     * @param  exchange          the exchange
     * @param  exchangeFormatter if provided then information about the exchange is included in the dump
     * @param  logStackTrace     whether to include a header for the stacktrace, to be added (not included in this
     *                           dump).
     * @return                   a human readable message history as a table
     */
    public static String dumpMessageHistoryStacktrace(
            Exchange exchange, ExchangeFormatter exchangeFormatter, boolean logStackTrace) {
        // must not cause new exceptions so run this in a try catch block
        try {
            return doDumpMessageHistoryStacktrace(exchange, exchangeFormatter, logStackTrace);
        } catch (Exception e) {
            // ignore as the body is for logging purpose
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static String doDumpMessageHistoryStacktrace(
            Exchange exchange, ExchangeFormatter exchangeFormatter, boolean logStackTrace) {

        // add incoming origin of message on the top
        String routeId = exchange.getFromRouteId();
        Route route = exchange.getContext().getRoute(routeId);
        String loc = route != null ? route.getSourceLocationShort() : null;
        if (loc == null) {
            loc = "";
        }
        String id = routeId;
        String label = "";
        if (exchange.getFromEndpoint() != null) {
            label = "from[" + URISupport.sanitizeUri(StringHelper.limitLength(exchange.getFromEndpoint().getEndpointUri(), 100))
                    + "]";
        }
        final long elapsed = exchange.getClock().elapsed();

        List<MessageHistory> list = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
        boolean enabled = list != null;
        boolean source = !loc.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("Message History");
        if (!source && !enabled) {
            sb.append(" (source location and message history is disabled)");
        } else if (!source) {
            sb.append(" (source location is disabled)");
        } else if (!enabled) {
            sb.append(" (complete message history is disabled)");
        }
        sb.append("\n");
        sb.append(
                "---------------------------------------------------------------------------------------------------------------------------------------\n");
        String goMessageHistoryHeader = exchange.getContext().getGlobalOption(Exchange.MESSAGE_HISTORY_HEADER_FORMAT);
        sb.append(String.format(goMessageHistoryHeader == null ? MESSAGE_HISTORY_HEADER : goMessageHistoryHeader,
                "Source", "ID", "Processor", "Elapsed (ms)"));
        sb.append("\n");

        String goMessageHistoryOutput = exchange.getContext().getGlobalOption(Exchange.MESSAGE_HISTORY_OUTPUT_FORMAT);
        goMessageHistoryOutput = goMessageHistoryOutput == null ? MESSAGE_HISTORY_OUTPUT : goMessageHistoryOutput;
        sb.append(String.format(goMessageHistoryOutput, loc, routeId + "/" + id, label, elapsed));
        sb.append("\n");

        if (list == null || list.isEmpty()) {
            // message history is not enabled but we can show the last processed
            // instead
            id = exchange.getExchangeExtension().getHistoryNodeId();
            if (id != null) {
                loc = exchange.getExchangeExtension().getHistoryNodeSource();
                if (loc == null) {
                    loc = "";
                }
                String rid = ExchangeHelper.getAtRouteId(exchange);
                if (rid != null) {
                    routeId = rid;
                }
                label = exchange.getExchangeExtension().getHistoryNodeLabel();
                // we need to avoid leak the sensible information here
                // the sanitizeUri takes a very long time for very long string
                // and the format cuts this to
                // 78 characters, anyway. Cut this to 100 characters. This will
                // give enough space for removing
                // characters in the sanitizeUri method and will be reasonably
                // fast
                label = URISupport.sanitizeUri(StringHelper.limitLength(label, 100));
                // we do not have elapsed time
                sb.append("\t...\n");
                sb.append(String.format(goMessageHistoryOutput, loc, routeId + "/" + id, label, 0));
                sb.append("\n");
            }
        } else {
            for (MessageHistory history : list) {
                // and then each history
                loc = LoggerHelper.getLineNumberLoggerName(history.getNode());
                if (loc == null) {
                    loc = "";
                }
                routeId = history.getRouteId() != null ? history.getRouteId() : "";
                id = history.getNode().getId();
                // we need to avoid leak the sensible information here
                // the sanitizeUri takes a very long time for very long string
                // and the format cuts this to
                // 78 characters, anyway. Cut this to 100 characters. This will
                // give enough space for removing
                // characters in the sanitizeUri method and will be reasonably
                // fast
                label = URISupport.sanitizeUri(StringHelper.limitLength(history.getNode().getLabel(), 100));

                sb.append(String.format(goMessageHistoryOutput, loc, routeId + "/" + id, label, history.getElapsed()));
                sb.append("\n");
            }
        }

        if (exchangeFormatter != null) {
            sb.append("\nExchange\n");
            sb.append(
                    "---------------------------------------------------------------------------------------------------------------------------------------\n");
            sb.append(exchangeFormatter.format(exchange));
            sb.append("\n");
        }

        if (logStackTrace) {
            sb.append("\nStacktrace\n");
            sb.append(
                    "---------------------------------------------------------------------------------------------------------------------------------------");
        }
        return sb.toString();
    }

    /**
     * Dumps the message as a generic JSon structure as text.
     *
     * @param  message the message
     * @return         the JSon
     */
    public static String dumpAsJSon(Message message) {
        return dumpAsJSon(message, true);
    }

    /**
     * Dumps the message as a generic JSon structure as text.
     *
     * @param  message     the message
     * @param  includeBody whether or not to include the message body
     * @return             the JSon
     */
    public static String dumpAsJSon(Message message, boolean includeBody) {
        return dumpAsJSon(message, includeBody, 0);
    }

    /**
     * Dumps the message as a generic JSon structure as text.
     *
     * @param  message     the message
     * @param  includeBody whether or not to include the message body
     * @param  indent      number of spaces to indent
     * @return             the JSon
     */
    public static String dumpAsJSon(Message message, boolean includeBody, int indent) {
        return dumpAsJSon(message, includeBody, indent, false, true, 128 * 1024, true);
    }

    /**
     * Dumps the message as a generic JSon structure as text.
     *
     * @param  message      the message
     * @param  includeBody  whether or not to include the message body
     * @param  indent       number of spaces to indent
     * @param  allowStreams whether to include message body if they are stream based
     * @param  allowFiles   whether to include message body if they are file based
     * @param  maxChars     clip body after maximum chars (to avoid very big messages). Use 0 or negative value to not
     *                      limit at all.
     * @return              the JSon
     */
    public static String dumpAsJSon(
            Message message, boolean includeBody, int indent, boolean allowStreams, boolean allowFiles, int maxChars,
            boolean pretty) {
        return dumpAsJSon(message, false, false, includeBody, indent, false, allowStreams, allowFiles, maxChars, pretty);
    }

    /**
     * Dumps the message as a generic JSon structure as text.
     *
     * @param  message                   the message
     * @param  includeExchangeProperties whether or not to include exchange properties
     * @param  includeExchangeVariables  whether or not to include exchange variables
     * @param  includeBody               whether or not to include the message body
     * @param  indent                    number of spaces to indent
     * @param  allowCachedStreams        whether to include message body if they are stream cached based
     * @param  allowStreams              whether to include message body if they are stream based
     * @param  allowFiles                whether to include message body if they are file based
     * @param  maxChars                  clip body after maximum chars (to avoid very big messages). Use 0 or negative
     *                                   value to not limit at all.
     * @param  pretty                    whether to pretty print JSon
     * @return                           the JSon
     */
    public static String dumpAsJSon(
            Message message, boolean includeExchangeProperties, boolean includeExchangeVariables, boolean includeBody,
            int indent,
            boolean allowCachedStreams, boolean allowStreams, boolean allowFiles, int maxChars, boolean pretty) {

        JsonObject jo = dumpAsJSonObject(message, includeExchangeProperties, includeExchangeVariables, includeBody,
                allowCachedStreams, allowStreams,
                allowFiles, maxChars);
        String answer = jo.toJson();
        if (pretty) {
            if (indent > 0) {
                answer = Jsoner.prettyPrint(answer, indent);
            } else {
                answer = Jsoner.prettyPrint(answer);
            }
        }
        return answer;
    }

    /**
     * Dumps the message as a generic JSon Object.
     *
     * @param  message                   the message
     * @param  includeExchangeProperties whether or not to include exchange properties
     * @param  includeExchangeVariables  whether or not to include exchange variables
     * @param  includeBody               whether or not to include the message body
     * @param  allowCachedStreams        whether to include message body if they are stream cached based
     * @param  allowStreams              whether to include message body if they are stream based
     * @param  allowFiles                whether to include message body if they are file based
     * @param  maxChars                  clip body after maximum chars (to avoid very big messages). Use 0 or negative
     *                                   value to not limit at all.
     * @return                           the JSon Object
     */
    public static JsonObject dumpAsJSonObject(
            Message message, boolean includeExchangeProperties, boolean includeExchangeVariables, boolean includeBody,
            boolean allowCachedStreams, boolean allowStreams, boolean allowFiles, int maxChars) {

        JsonObject root = new JsonObject();
        JsonObject jo = new JsonObject();
        root.put("message", jo);
        jo.put("exchangeId", message.getExchange().getExchangeId());
        jo.put("exchangePattern", message.getExchange().getPattern().name());
        jo.put("exchangeType", ObjectHelper.classCanonicalName(message.getExchange()));
        jo.put("messageType", ObjectHelper.classCanonicalName(message));

        // exchange variables
        if (includeExchangeVariables && message.getExchange().hasVariables()) {
            JsonArray arr = new JsonArray();
            // sort the exchange variables so they are listed A..Z
            Map<String, Object> properties = new TreeMap<>(message.getExchange().getVariables());
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Object value = entry.getValue();
                String type = ObjectHelper.classCanonicalName(value);
                JsonObject jh = new JsonObject();
                String key = entry.getKey();
                jh.put("key", key);
                if (type != null) {
                    jh.put("type", type);
                }
                if (value != null) {
                    Object s = Jsoner.trySerialize(value);
                    if (s == null) {
                        // cannot JSon serialize out of the box, so we need to use string value
                        try {
                            s = extractValueForLogging(value, message, allowCachedStreams, allowStreams, allowFiles, maxChars);
                        } catch (Exception e) {
                            // ignore
                        }
                    } else {
                        // use the value as-is because it can be serialized in json
                        s = value;
                    }
                    jh.put("value", s);
                }
                arr.add(jh);
            }
            if (!arr.isEmpty()) {
                jo.put("exchangeVariables", arr);
            }
        }
        // exchange properties
        if (includeExchangeProperties && message.getExchange().hasProperties()) {
            JsonArray arr = new JsonArray();
            // sort the exchange properties so they are listed A..Z
            Map<String, Object> properties = new TreeMap<>(message.getExchange().getAllProperties());
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Object value = entry.getValue();
                String type = ObjectHelper.classCanonicalName(value);
                JsonObject jh = new JsonObject();
                String key = entry.getKey();
                // skip message history
                if (Exchange.MESSAGE_HISTORY.equals(key)) {
                    continue;
                }
                jh.put("key", key);
                if (type != null) {
                    jh.put("type", type);
                }
                if (value != null) {
                    Object s = Jsoner.trySerialize(value);
                    if (s == null) {
                        // cannot JSon serialize out of the box, so we need to use string value
                        try {
                            s = extractValueForLogging(value, message, allowCachedStreams, allowStreams, allowFiles, maxChars);
                        } catch (Exception e) {
                            // ignore
                        }
                    } else {
                        // use the value as-is because it can be serialized in json
                        s = value;
                    }
                    jh.put("value", s);
                }
                arr.add(jh);
            }
            if (!arr.isEmpty()) {
                jo.put("exchangeProperties", arr);
            }
        }
        // headers
        if (message.hasHeaders()) {
            JsonArray arr = new JsonArray();
            // sort the headers so they are listed A..Z
            Map<String, Object> headers = new TreeMap<>(message.getHeaders());
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                Object value = entry.getValue();
                String type = ObjectHelper.classCanonicalName(value);
                JsonObject jh = new JsonObject();
                jh.put("key", entry.getKey());
                if (type != null) {
                    jh.put("type", type);
                }
                // dump header value as JSon, use Camel type converter to convert to String
                if (value != null) {
                    Object s = Jsoner.trySerialize(value);
                    if (s == null) {
                        // cannot JSon serialize out of the box, so we need to use string value
                        try {
                            s = extractValueForLogging(value, message, allowCachedStreams, allowStreams, allowFiles, maxChars);
                        } catch (Exception e) {
                            // ignore
                        }
                    } else {
                        // use the value as-is because it can be serialized in json
                        s = value;
                    }
                    jh.put("value", s);
                }
                arr.add(jh);
            }
            if (!arr.isEmpty()) {
                jo.put("headers", arr);
            }
        }
        if (includeBody) {
            JsonObject jb = new JsonObject();
            jo.put("body", jb);
            Object body = message.getBody();
            String type = ObjectHelper.classCanonicalName(body);
            if (type != null) {
                jb.put("type", type);
            }
            if (body instanceof Collection) {
                long size = ((Collection<?>) body).size();
                jb.put("size", size);
            }
            if (body != null && body.getClass().isArray()) {
                int size = Array.getLength(body);
                jb.put("size", size);
            }
            if (body instanceof StreamCache) {
                long pos = ((StreamCache) body).position();
                if (pos != -1) {
                    jb.put("position", pos);
                }
            }
            String data = extractBodyForLogging(message, null, allowCachedStreams, allowStreams, allowFiles, maxChars);
            if (data != null) {
                jb.put("value", Jsoner.escape(data));
            }
        }

        return root;
    }

    /**
     * Dumps the exception as a generic XML structure.
     *
     * @param  indent number of spaces to indent
     * @return        the XML
     */
    public static String dumpExceptionAsXML(Throwable exception, int indent) {
        StringBuilder prefix = new StringBuilder();
        prefix.append(" ".repeat(indent));

        StringBuilder sb = new StringBuilder();
        try {
            sb.append(prefix).append("<exception");
            String type = ObjectHelper.classCanonicalName(exception);
            if (type != null) {
                sb.append(" type=\"").append(type).append("\"");
            }
            String msg = exception.getMessage();
            if (msg != null) {
                msg = StringHelper.xmlEncode(msg);
                sb.append(" message=\"").append(msg).append("\"");
            }
            sb.append(">\n");
            StringWriter sw = new StringWriter();
            exception.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            // must always xml encode
            sb.append(StringHelper.xmlEncode(trace));
            sb.append(prefix).append("</exception>");
        } catch (Exception e) {
            // ignore
        }

        return sb.toString();
    }

    /**
     * Dumps the exception as a generic JSon structure as text.
     *
     * @param  indent number of spaces to indent
     * @param  pretty whether to pretty print JSon
     * @return        the JSon
     */
    public static String dumpExceptionAsJSon(Throwable exception, int indent, boolean pretty) {
        JsonObject jo = dumpExceptionAsJSonObject(exception);
        String answer = jo.toJson();
        if (pretty) {
            if (indent > 0) {
                answer = Jsoner.prettyPrint(answer, indent);
            } else {
                answer = Jsoner.prettyPrint(answer);
            }
        }
        return answer;
    }

    /**
     * Dumps the exception as a generic JSon object.
     *
     * @return the JSon object
     */
    public static JsonObject dumpExceptionAsJSonObject(Throwable exception) {
        JsonObject root = new JsonObject();
        JsonObject jo = new JsonObject();
        root.put("exception", jo);

        String type = ObjectHelper.classCanonicalName(exception);
        if (type != null) {
            jo.put("type", type);
        }
        String msg = exception.getMessage();
        jo.put("message", msg);
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        try {
            jo.put("stackTrace", Jsoner.escape(trace));
        } catch (Exception e) {
            // ignore as the body is for logging purpose
        }
        return root;
    }

}
