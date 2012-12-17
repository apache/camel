/**
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
package org.apache.camel.util;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.BytesSource;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.StringSource;
import org.apache.camel.WrappedFile;

/**
 * Some helper methods when working with {@link org.apache.camel.Message}.
 * 
 * @version
 */
public final class MessageHelper {

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
     * @return the body typename as String, can return
     *         <tt>null</null> if no body
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
        if (message.getBody() instanceof StreamCache) {
            ((StreamCache)message.getBody()).reset();
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
            String property = message.getExchange().getContext().getProperty(Exchange.LOG_DEBUG_BODY_STREAMS);
            if (property != null) {
                streams = message.getExchange().getContext().getTypeConverter().convertTo(Boolean.class, message.getExchange(), property);
            }
        }

        // default to 1000 chars
        int maxChars = 1000;

        if (message.getExchange() != null) {
            String property = message.getExchange().getContext().getProperty(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
            if (property != null) {
                maxChars = message.getExchange().getContext().getTypeConverter().convertTo(Integer.class, property);
            }
        }

        return extractBodyForLogging(message, prepend, streams, false, maxChars);
    }

    /**
     * Extracts the body for logging purpose.
     * <p/>
     * Will clip the body if its too big for logging.
     * 
     * @see org.apache.camel.Exchange#LOG_DEBUG_BODY_MAX_CHARS
     * @param message the message
     * @param prepend a message to prepend
     * @param allowStreams whether or not streams is allowed
     * @param allowFiles whether or not files is allowed
     * @param maxChars limit to maximum number of chars. Use 0 or negative value
     *            to not limit at all.
     * @return the logging message
     */
    public static String extractBodyForLogging(Message message, String prepend, boolean allowStreams, boolean allowFiles, int maxChars) {
        Object obj = message.getBody();
        if (obj == null) {
            return prepend + "[Body is null]";
        }

        if (!allowStreams) {
            if (obj instanceof StreamSource && !(obj instanceof StringSource || obj instanceof BytesSource)) {
                /*
                 * Generally do not log StreamSources but as StringSource and
                 * ByteSoure are memory based they are ok
                 */
                return prepend + "[Body is instance of java.xml.transform.StreamSource]";
            } else if (obj instanceof StreamCache) {
                return prepend + "[Body is instance of org.apache.camel.StreamCache]";
            } else if (obj instanceof InputStream) {
                return prepend + "[Body is instance of java.io.InputStream]";
            } else if (obj instanceof OutputStream) {
                return prepend + "[Body is instance of java.io.OutputStream]";
            } else if (obj instanceof Reader) {
                return prepend + "[Body is instance of java.io.Reader]";
            } else if (obj instanceof Writer) {
                return prepend + "[Body is instance of java.io.Writer]";
            } else if (obj instanceof WrappedFile || obj instanceof File) {
                return prepend + "[Body is file based: " + obj + "]";
            }
        }

        // is the body a stream cache
        StreamCache cache;
        if (obj instanceof StreamCache) {
            cache = (StreamCache)obj;
        } else {
            cache = null;
        }

        // grab the message body as a string
        String body = null;
        if (message.getExchange() != null) {
            try {
                body = message.getExchange().getContext().getTypeConverter().convertTo(String.class, message.getExchange(), obj);
            } catch (Exception e) {
                // ignore as the body is for logging purpose
            }
        }
        if (body == null) {
            body = obj.toString();
        }

        // reset stream cache after use
        if (cache != null) {
            cache.reset();
        }

        if (body == null) {
            return prepend + "[Body is null]";
        }

        // clip body if length enabled and the body is too big
        if (maxChars > 0 && body.length() > maxChars) {
            body = body.substring(0, maxChars) + "... [Body clipped after " + maxChars + " chars, total length is " + body.length() + "]";
        }

        return prepend + body;
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
        StringBuilder sb = new StringBuilder();
        // include exchangeId as attribute on the <message> tag
        sb.append("<message exchangeId=\"").append(message.getExchange().getExchangeId()).append("\">\n");

        // headers
        if (message.hasHeaders()) {
            sb.append("<headers>\n");
            // sort the headers so they are listed A..Z
            Map<String, Object> headers = new TreeMap<String, Object>(message.getHeaders());
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                Object value = entry.getValue();
                String type = ObjectHelper.classCanonicalName(value);
                sb.append("<header key=\"" + entry.getKey() + "\"");
                if (type != null) {
                    sb.append(" type=\"" + type + "\"");
                }
                sb.append(">");

                // dump header value as XML, use Camel type converter to convert
                // to String
                if (value != null) {
                    try {
                        String xml = message.getExchange().getContext().getTypeConverter().convertTo(String.class, 
                                message.getExchange(), value);
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
            sb.append("</headers>\n");
        }

        if (includeBody) {
            sb.append("<body");
            String type = ObjectHelper.classCanonicalName(message.getBody());
            if (type != null) {
                sb.append(" type=\"" + type + "\"");
            }
            sb.append(">");

            // dump body value as XML, use Camel type converter to convert to
            // String
            // do not allow streams, but allow files, and clip very big message
            // bodies (128kb)
            String xml = extractBodyForLogging(message, "", false, true, 128 * 1024);
            if (xml != null) {
                // must always xml encode
                sb.append(StringHelper.xmlEncode(xml));
            }

            sb.append("</body>\n");
        }

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
        if (!source.hasHeaders()) {
            return;
        }

        for (Map.Entry<String, Object> entry : source.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (target.getHeader(key) == null || override) {
                target.setHeader(key, value);
            }
        }
    }

}
