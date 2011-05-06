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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.jaxp.BytesSource;
import org.apache.camel.converter.jaxp.StringSource;

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
     * Extracts the given body and returns it as a String, that
     * can be used for logging etc.
     * <p/>
     * Will handle stream based bodies wrapped in StreamCache.
     *
     * @param message  the message with the body
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
     * @param message  the message with the body
     * @return the body typename as String, can return <tt>null</null> if no body
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
     * If the message body contains a {@link StreamCache} instance, reset the cache to 
     * enable reading from it again.
     * 
     * @param message the message for which to reset the body
     */
    public static void resetStreamCache(Message message) {
        if (message == null) {
            return;
        }
        if (message.getBody() instanceof StreamCache) {
            ((StreamCache) message.getBody()).reset();
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
     * Will clip the body if its too big for logging.
     * Will prepend the message with <tt>Message: </tt>
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
            String property = message.getExchange().getContext().getProperties().get(Exchange.LOG_DEBUG_BODY_STREAMS);
            if (property != null) {
                streams = message.getExchange().getContext().getTypeConverter().convertTo(Boolean.class, property);
            }
        }

        // default to 1000 chars
        int maxChars = 1000;

        if (message.getExchange() != null) {
            String property = message.getExchange().getContext().getProperties().get(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
            if (property != null) {
                maxChars = message.getExchange().getContext().getTypeConverter().convertTo(Integer.class, property);
            }
        }
        return extractBodyForLogging(message, prepend, streams, maxChars);
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
     * @param maxChars limit to maximum number of chars. Use 0 or negative value to not limit at all.
     * @return the logging message
     */
    public static String extractBodyForLogging(Message message, String prepend, boolean allowStreams, int maxChars) {
        Object obj = message.getBody();
        if (obj == null) {
            return prepend + "[Body is null]";
        }

        if (obj instanceof StringSource || obj instanceof BytesSource) {
            // these two are okay
        } else if (!allowStreams && obj instanceof StreamCache) {
            return prepend + "[Body is instance of org.apache.camel.StreamCache]";
        } else if (!allowStreams && obj instanceof StreamSource) {
            return prepend + "[Body is instance of java.xml.transform.StreamSource]";
        } else if (!allowStreams && obj instanceof InputStream) {
            return prepend + "[Body is instance of java.io.InputStream]";
        } else if (!allowStreams && obj instanceof OutputStream) {
            return prepend + "[Body is instance of java.io.OutputStream]";
        } else if (!allowStreams && obj instanceof Reader) {
            return prepend + "[Body is instance of java.io.Reader]";
        } else if (!allowStreams && obj instanceof Writer) {
            return prepend + "[Body is instance of java.io.Writer]";
        }

        // is the body a stream cache
        StreamCache cache;
        if (obj instanceof StreamCache) {
            cache = (StreamCache) obj;
        } else {
            cache = null;
        }

        // grab the message body as a string
        String body;
        if (message.getExchange() != null) {
            body = message.getExchange().getContext().getTypeConverter().convertTo(String.class, obj);
        } else {
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

}
