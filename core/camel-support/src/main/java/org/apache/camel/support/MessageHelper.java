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
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * Some helper methods when working with {@link org.apache.camel.Message}.
 */
public final class MessageHelper {

    private static final String MESSAGE_HISTORY_HEADER = "%-20s %-20s %-80s %-12s";
    private static final String MESSAGE_HISTORY_OUTPUT = "[%-18.18s] [%-18.18s] [%-78.78s] [%10.10s]";

    /**
     * Utility classes should not have a public constructor.
     */
    private MessageHelper() {
    }

    /**
     * Extracts the given body and returns it as a String, that can be used for
     * logging etc.
     * <p/>
     * Will handle stream based bodies wrapped in StreamCache.
     * 
     * @param message the message with the body
     * @return the body as String, can return <tt>null</null> if no body
     */
    public static String extractBodyAsString(Message message) {
        if (message == null) {
            return null;
        }

        // optimize if the body is a String type already
        Object body = message.getBody();
        if (body instanceof String) {
            return (String)body;
        }

        // we need to favor using stream cache so the body can be re-read later
        StreamCache newBody = message.getBody(StreamCache.class);
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
     * @param message the message with the body
     * @return the body type name as String, can return <tt>null</null> if no
     *         body
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
     * If the message body contains a {@link StreamCache} instance, reset the
     * cache to enable reading from it again.
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
        } catch (Throwable e) {
            // ignore
        }
        if (body instanceof StreamCache) {
            ((StreamCache)body).reset();
        }
    }

    /**
     * Returns the MIME content type on the message or <tt>null</tt> if none
     * defined
     */
    public static String getContentType(Message message) {
        return message.getHeader(Exchange.CONTENT_TYPE, String.class);
    }

    /**
     * Returns the MIME content encoding on the message or <tt>null</tt> if none
     * defined
     */
    public static String getContentEncoding(Message message) {
        return message.getHeader(Exchange.CONTENT_ENCODING, String.class);
    }

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging. Will prepend the message
     * with <tt>Message: </tt>
     * 
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_STREAMS
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     * @param message the message
     * @return the logging message
     */
    public static String extractBodyForLogging(Message message) {
        return extractBodyForLogging(message, "Message: ");
    }

    /**
     * Extracts the value for logging purpose.
     * <p/>
     * Will clip the value if its too big for logging.
     *
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_STREAMS
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     * @param value the value
     * @param message the message
     * @return the logging message
     */
    public static String extractValueForLogging(Object value, Message message) {
        boolean streams = false;
        if (message.getExchange() != null) {
            String globalOption = message.getExchange().getContext().getGlobalOption(Exchange.LOG_DEBUG_BODY_STREAMS);
            if (globalOption != null) {
                streams = message.getExchange().getContext().getTypeConverter().convertTo(Boolean.class, message.getExchange(), globalOption);
            }
        }

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

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging.
     *
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_STREAMS
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     * @param message the message
     * @param prepend a message to prepend
     * @return the logging message
     */
    public static String extractBodyForLogging(Message message, String prepend) {
        boolean streams = false;
        if (message.getExchange() != null) {
            String globalOption = message.getExchange().getContext().getGlobalOption(Exchange.LOG_DEBUG_BODY_STREAMS);
            if (globalOption != null) {
                streams = message.getExchange().getContext().getTypeConverter().convertTo(Boolean.class, message.getExchange(), globalOption);
            }
        }
        return extractBodyForLogging(message, prepend, streams, false);
    }

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging.
     *
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_STREAMS
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     * @param message the message
     * @param prepend a message to prepend
     * @param allowStreams whether or not streams is allowed
     * @param allowFiles whether or not files is allowed (currently not in use)
     * @return the logging message
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
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     * @param message the message
     * @param prepend a message to prepend (optional)
     * @param allowStreams whether or not streams is allowed
     * @param allowFiles whether or not files is allowed (currently not in use)
     * @param maxChars limit to maximum number of chars. Use 0 for not limit,
     *            and -1 for turning logging message body off.
     * @return the logging message
     */
    public static String extractBodyForLogging(Message message, String prepend, boolean allowStreams, boolean allowFiles, int maxChars) {
        String value = extractValueForLogging(message.getBody(), message, allowStreams, allowFiles, maxChars);
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
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     * @param obj the value
     * @param message the message
     * @param allowStreams whether or not streams is allowed
     * @param allowFiles whether or not files is allowed (currently not in use)
     * @param maxChars limit to maximum number of chars. Use 0 for not limit,
     *            and -1 for turning logging message body off.
     * @return the logging message
     */
    public static String extractValueForLogging(Object obj, Message message, boolean allowStreams, boolean allowFiles, int maxChars) {
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

        // is the body a stream cache or input stream
        StreamCache cache = null;
        InputStream is = null;
        if (obj instanceof StreamCache) {
            cache = (StreamCache)obj;
            is = null;
        } else if (obj instanceof InputStream) {
            cache = null;
            is = (InputStream)obj;
        }

        // grab the message body as a string
        String body = null;
        if (message.getExchange() != null) {
            try {
                body = message.getExchange().getContext().getTypeConverter().tryConvertTo(String.class, message.getExchange(), obj);
            } catch (Throwable e) {
                // ignore as the body is for logging purpose
            }
        }
        if (body == null) {
            try {
                body = obj.toString();
            } catch (Throwable e) {
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
            body = body.substring(0, maxChars) + "... [Body clipped after " + maxChars + " chars, total length is " + body.length() + "]";
        }

        return body;
    }

    /**
     * Dumps the message as a generic XML structure.
     * 
     * @param message the message
     * @return the XML
     */
    public static String dumpAsXml(Message message) {
        return dumpAsXml(message, true);
    }

    /**
     * Dumps the message as a generic XML structure.
     * 
     * @param message the message
     * @param includeBody whether or not to include the message body
     * @return the XML
     */
    public static String dumpAsXml(Message message, boolean includeBody) {
        return dumpAsXml(message, includeBody, 0);
    }

    /**
     * Dumps the message as a generic XML structure.
     *
     * @param message the message
     * @param includeBody whether or not to include the message body
     * @param indent number of spaces to indent
     * @return the XML
     */
    public static String dumpAsXml(Message message, boolean includeBody, int indent) {
        return dumpAsXml(message, includeBody, indent, false, true, 128 * 1024);
    }

    /**
     * Dumps the message as a generic XML structure.
     *
     * @param message the message
     * @param includeBody whether or not to include the message body
     * @param indent number of spaces to indent
     * @param allowStreams whether to include message body if they are stream
     *            based
     * @param allowFiles whether to include message body if they are file based
     * @param maxChars clip body after maximum chars (to avoid very big
     *            messages). Use 0 or negative value to not limit at all.
     * @return the XML
     */
    public static String dumpAsXml(Message message, boolean includeBody, int indent, boolean allowStreams, boolean allowFiles, int maxChars) {
        StringBuilder sb = new StringBuilder();

        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            prefix.append(" ");
        }

        // include exchangeId as attribute on the <message> tag
        sb.append(prefix);
        sb.append("<message exchangeId=\"").append(message.getExchange().getExchangeId()).append("\">\n");

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

                // dump header value as XML, use Camel type converter to convert
                // to String
                if (value != null) {
                    try {
                        String xml = message.getExchange().getContext().getTypeConverter().tryConvertTo(String.class, message.getExchange(), value);
                        if (xml != null) {
                            // must always xml encode
                            sb.append(StringHelper.xmlEncode(xml));
                        }
                    } catch (Throwable e) {
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
            String type = ObjectHelper.classCanonicalName(message.getBody());
            if (type != null) {
                sb.append(" type=\"").append(type).append("\"");
            }
            sb.append(">");

            String xml = extractBodyForLogging(message, null, allowStreams, allowFiles, maxChars);
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
     * Copies the headers from the source to the target message.
     * 
     * @param source the source message
     * @param target the target message
     * @param override whether to override existing headers
     */
    public static void copyHeaders(Message source, Message target, boolean override) {
        copyHeaders(source, target, null, override);
    }

    /**
     * Copies the headers from the source to the target message.
     * 
     * @param source the source message
     * @param target the target message
     * @param strategy the header filter strategy which could help us to filter
     *            the protocol message headers
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
     * Dumps the {@link MessageHistory} from the {@link Exchange} in a human
     * readable format.
     *
     * @param exchange the exchange
     * @param exchangeFormatter if provided then information about the exchange
     *            is included in the dump
     * @param logStackTrace whether to include a header for the stacktrace, to
     *            be added (not included in this dump).
     * @return a human readable message history as a table
     */
    public static String dumpMessageHistoryStacktrace(Exchange exchange, ExchangeFormatter exchangeFormatter, boolean logStackTrace) {
        // must not cause new exceptions so run this in a try catch block
        try {
            return doDumpMessageHistoryStacktrace(exchange, exchangeFormatter, logStackTrace);
        } catch (Throwable e) {
            // ignore as the body is for logging purpose
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static String doDumpMessageHistoryStacktrace(Exchange exchange, ExchangeFormatter exchangeFormatter, boolean logStackTrace) {
        List<MessageHistory> list = exchange.getProperty(Exchange.MESSAGE_HISTORY, List.class);
        boolean enabled = list != null;

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("Message History");
        if (!enabled) {
            sb.append(" (complete message history is disabled)");
        }
        sb.append("\n");
        sb.append("---------------------------------------------------------------------------------------------------------------------------------------\n");
        String goMessageHistoryHeader = exchange.getContext().getGlobalOption(Exchange.MESSAGE_HISTORY_HEADER_FORMAT);
        sb.append(String.format(goMessageHistoryHeader == null ? MESSAGE_HISTORY_HEADER : goMessageHistoryHeader, "RouteId", "ProcessorId", "Processor", "Elapsed (ms)"));
        sb.append("\n");

        // add incoming origin of message on the top
        String routeId = exchange.getFromRouteId();
        String id = routeId;
        String label = "";
        if (exchange.getFromEndpoint() != null) {
            label = "from[" + URISupport.sanitizeUri(exchange.getFromEndpoint().getEndpointUri() + "]");
        }
        long elapsed = new StopWatch(exchange.getCreated()).taken();

        String goMessageHistoryOutput = exchange.getContext().getGlobalOption(Exchange.MESSAGE_HISTORY_OUTPUT_FORMAT);
        goMessageHistoryOutput = goMessageHistoryOutput == null ? MESSAGE_HISTORY_OUTPUT : goMessageHistoryOutput;
        sb.append(String.format(goMessageHistoryOutput, routeId, id, label, elapsed));
        sb.append("\n");

        if (list == null || list.isEmpty()) {
            // message history is not enabled but we can show the last processed
            // instead
            id = exchange.adapt(ExtendedExchange.class).getHistoryNodeId();
            if (id != null) {
                // compute route id
                String rid = ExchangeHelper.getAtRouteId(exchange);
                if (rid != null) {
                    routeId = rid;
                }
                label = exchange.adapt(ExtendedExchange.class).getHistoryNodeLabel();
                // we need to avoid leak the sensible information here
                // the sanitizeUri takes a very long time for very long string
                // and the format cuts this to
                // 78 characters, anyway. Cut this to 100 characters. This will
                // give enough space for removing
                // characters in the sanitizeUri method and will be reasonably
                // fast
                label = URISupport.sanitizeUri(StringHelper.limitLength(label, 100));
                // we do not have elapsed time
                elapsed = 0;
                sb.append("\t...\n");
                sb.append(String.format(goMessageHistoryOutput, routeId, id, label, elapsed));
                sb.append("\n");
            }
        } else {
            for (MessageHistory history : list) {
                // and then each history
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
                elapsed = history.getElapsed();

                sb.append(String.format(goMessageHistoryOutput, routeId, id, label, elapsed));
                sb.append("\n");
            }
        }

        if (exchangeFormatter != null) {
            sb.append("\nExchange\n");
            sb.append("---------------------------------------------------------------------------------------------------------------------------------------\n");
            sb.append(exchangeFormatter.format(exchange));
            sb.append("\n");
        }

        if (logStackTrace) {
            sb.append("\nStacktrace\n");
            sb.append("---------------------------------------------------------------------------------------------------------------------------------------");
        }
        return sb.toString();
    }

}
