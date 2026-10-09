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

package org.apache.camel.component.cloudevents.transformer;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.cloudevents.CloudEvents;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;

/**
 * Data type represents a default Camel CloudEvent V1 Json format binding. The data type reads Camel specific CloudEvent
 * headers and transforms these to a Json object representing the CloudEvents Json format specification. Sets default
 * values for CloudEvent attributes such as the Http content type header, event source, event type.
 * <p/>
 * The body is written as the data of the event according to its datacontenttype (CloudEvents Json format, section 3.1):
 * <ul>
 * <li>declared Json content type (subtype {@code json} or suffix {@code +json}): a body that is a Json value, scalars
 * included, is nested as that value; any other body is a Json string</li>
 * <li>no declared content type ({@code application/json} is assumed): only a Json object or array is nested, so that
 * text such as a long number or {@code null} stays a string</li>
 * <li>any other content type: the body is a Json string</li>
 * <li>a {@code byte[]} body that is not valid text in the charset of the exchange: {@code data_base64}, with the
 * datacontenttype {@code application/octet-stream} unless one was declared</li>
 * <li>a null body: no data</li>
 * </ul>
 */
@DataTypeTransformer(name = "application-cloudevents+json",
                     description = "Adds default CloudEvent (JSon binding) headers to the Camel message (such as content-type, event source, event type etc.)")
public class CloudEventJsonDataTypeTransformer extends Transformer {

    public static final String APPLICATION_CLOUDEVENTS_JSON = "application/cloudevents+json";
    public static final String APPLICATION_JSON = "application/json";

    // the states of the Json scanner in isJson
    private static final int VALUE = 0;
    private static final int VALUE_OR_END = 1;
    private static final int KEY = 2;
    private static final int KEY_OR_END = 3;
    private static final int COLON = 4;
    private static final int COMMA_OR_END = 5;

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final Map<String, Object> headers = message.getHeaders();

        String dataContentType = headers.getOrDefault(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, APPLICATION_JSON).toString();
        // whether the content type of the data was declared, rather than assumed to be application/json
        boolean contentTypeDeclared = headers.get(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE) != null
                || headers.get(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE) != null;
        if (!APPLICATION_CLOUDEVENTS_JSON.equals(dataContentType)) {
            Map<String, Object> cloudEventAttributes = new HashMap<>();
            CloudEvent cloudEvent = CloudEvents.v1_0;
            for (CloudEvent.Attribute attribute : cloudEvent.attributes()) {
                if (headers.containsKey(attribute.id())) {
                    cloudEventAttributes.put(attribute.json(), headers.get(attribute.id()));
                }
            }

            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_VERSION).json(),
                    cloudEvent.version());
            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_ID).json(),
                    message.getExchange().getExchangeId());
            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).json(),
                    CloudEvent.DEFAULT_CAMEL_CLOUD_EVENT_TYPE);
            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).json(),
                    CloudEvent.DEFAULT_CAMEL_CLOUD_EVENT_SOURCE);

            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TIME).json(),
                    cloudEvent.getEventTime(message.getExchange()));

            String dataContentTypeKey = cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json();
            cloudEventAttributes.putIfAbsent(dataContentTypeKey, dataContentType);
            // the data is written as the datacontenttype of the event says
            Object eventDataContentType = cloudEventAttributes.get(dataContentTypeKey);
            boolean jsonData = isJsonContentType(eventDataContentType != null ? eventDataContentType.toString() : null);
            if (message.getBody() instanceof byte[] bytes) {
                String text = decodeText(bytes, ExchangeHelper.getCharset(message.getExchange()));
                if (text != null) {
                    cloudEventAttributes.putIfAbsent("data", text);
                } else {
                    // decoding these bytes as text would replace the invalid ones, so the data would be lost
                    cloudEventAttributes.putIfAbsent("data_base64", Base64.getEncoder().encodeToString(bytes));
                    // a declared datacontenttype is kept: the route states the format of these bytes, which may be valid
                    // in a charset other than the one of the exchange, so only an assumed application/json is replaced
                    if (!contentTypeDeclared) {
                        // these bytes are not Json, and the datacontenttype must reflect the format of the data
                        cloudEventAttributes.put(dataContentTypeKey, CloudEvent.APPLICATION_OCTET_STREAM_MIME_TYPE);
                    }
                }
            } else {
                String data = MessageHelper.extractBodyAsString(message);
                // the data is optional, so a null body has none rather than the text "null"
                if (data != null) {
                    cloudEventAttributes.putIfAbsent("data", data);
                }
            }

            headers.put(Exchange.CONTENT_TYPE, APPLICATION_CLOUDEVENTS_JSON);

            message.setBody(createCloudEventJsonObject(cloudEventAttributes, jsonData, contentTypeDeclared));

            cloudEvent.attributes().stream().map(CloudEvent.Attribute::id).forEach(headers::remove);
        }
    }

    private String createCloudEventJsonObject(
            Map<String, Object> cloudEventAttributes, boolean jsonData, boolean contentTypeDeclared) {
        StringBuilder builder = new StringBuilder("{");

        cloudEventAttributes.forEach((key, value) -> {
            builder.append(" ");
            appendJsonString(builder, key);
            builder.append(":");
            if (jsonData && "data".equals(key) && value instanceof String data
                    && (contentTypeDeclared ? isJsonValue(data) : isJson(data))) {
                // set Json data as nested value in the data field, a scalar only if Json was declared
                builder.append(data);
            } else {
                appendJsonString(builder, String.valueOf(value));
            }
            builder.append(",");
        });

        if (!cloudEventAttributes.isEmpty()) {
            builder.deleteCharAt(builder.lastIndexOf(","));
        }

        return builder.append("}").toString();
    }

    /**
     * Appends the value as a Json string, escaping the quote, the backslash and the control characters (RFC 8259,
     * section 7).
     */
    private static void appendJsonString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
    }

    /**
     * Whether the content type declares Json data: the subtype of its media type, without parameters, is {@code json}
     * or ends with {@code +json} (CloudEvents Json format, section 3.1). An absent content type is Json, as the
     * transformer declares {@code application/json} then.
     */
    static boolean isJsonContentType(String contentType) {
        if (contentType == null) {
            return true;
        }
        String subtype = mediaSubtype(contentType);
        return subtype != null && (subtype.equals("json") || subtype.endsWith("+json"));
    }

    /**
     * The bytes decoded as text in the given charset, or {@code null} if they are not valid text in it.
     */
    static String decodeText(byte[] data, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String mediaType(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }

    private static String mediaSubtype(String contentType) {
        String mediaType = mediaType(contentType);
        int slash = mediaType.indexOf('/');
        return slash < 0 ? null : mediaType.substring(slash + 1);
    }

    /**
     * Whether the data is a Json value: an object, an array, a string, a number, true, false or null.
     */
    static boolean isJsonValue(String data) {
        if (data == null) {
            return false;
        }
        final int length = data.length();
        int i = skipWhitespace(data, 0, length);
        if (i == length) {
            return false;
        }
        char ch = data.charAt(i);
        if (ch == '{' || ch == '[') {
            return isJson(data);
        }
        i = skipScalar(data, i, length);
        // nothing but whitespace may follow the value
        return i >= 0 && skipWhitespace(data, i, length) == length;
    }

    /**
     * Whether the data is a Json object or array, which is then set as nested Json value. Text that only starts like
     * Json (such as a log line "[INFO] ...") is set as a Json string.
     * <p/>
     * The data is checked with a single pass over its characters that validates the Json grammar (RFC 8259) without
     * building the objects, so a large Json body costs little more than the copy into the event, and a body that is not
     * valid Json can never make the event invalid Json.
     */
    static boolean isJson(String data) {
        if (data == null) {
            return false;
        }
        final int length = data.length();
        int i = skipWhitespace(data, 0, length);
        if (i == length || data.charAt(i) != '{' && data.charAt(i) != '[') {
            return false;
        }

        // the open objects (true) and arrays (false), which grows only for deeply nested data
        boolean[] objects = new boolean[32];
        int depth = 0;
        int state = VALUE;
        while (true) {
            i = skipWhitespace(data, i, length);
            if (i == length) {
                // unterminated object or array
                return false;
            }
            char ch = data.charAt(i);
            if ((state == VALUE_OR_END && ch == ']') || (state == KEY_OR_END && ch == '}')
                    || (state == COMMA_OR_END && ch == (objects[depth - 1] ? '}' : ']'))) {
                // closes the current object or array
                i++;
                depth--;
                if (depth == 0) {
                    // nothing but whitespace may follow the closed value
                    return skipWhitespace(data, i, length) == length;
                }
                state = COMMA_OR_END;
            } else if (state == VALUE || state == VALUE_OR_END) {
                if (ch == '{' || ch == '[') {
                    if (depth == objects.length) {
                        objects = Arrays.copyOf(objects, depth * 2);
                    }
                    objects[depth++] = ch == '{';
                    i++;
                    state = ch == '{' ? KEY_OR_END : VALUE_OR_END;
                } else {
                    i = skipScalar(data, i, length);
                    state = COMMA_OR_END;
                }
            } else if (state == KEY || state == KEY_OR_END) {
                i = ch == '"' ? skipString(data, i, length) : -1;
                state = COLON;
            } else if (state == COLON) {
                i = ch == ':' ? i + 1 : -1;
                state = VALUE;
            } else if (ch == ',') {
                // state is COMMA_OR_END
                i++;
                state = objects[depth - 1] ? KEY : VALUE;
            } else {
                return false;
            }
            if (i < 0) {
                return false;
            }
        }
    }

    private static int skipWhitespace(String data, int index, int length) {
        while (index < length) {
            char ch = data.charAt(index);
            if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r') {
                break;
            }
            index++;
        }
        return index;
    }

    /**
     * Skips a Json string, number, true, false or null at the index, and returns the index after it, or -1 if it is not
     * valid.
     */
    private static int skipScalar(String data, int index, int length) {
        char ch = data.charAt(index);
        if (ch == '"') {
            return skipString(data, index, length);
        } else if (ch == '-' || (ch >= '0' && ch <= '9')) {
            return skipNumber(data, index, length);
        } else if (data.startsWith("true", index)) {
            return index + 4;
        } else if (data.startsWith("false", index)) {
            return index + 5;
        } else if (data.startsWith("null", index)) {
            return index + 4;
        }
        return -1;
    }

    /**
     * Skips the Json string that starts with the quote at the index, and returns the index after its closing quote, or
     * -1 if it is not valid (unterminated, an unknown escape, or a raw control character).
     */
    private static int skipString(String data, int index, int length) {
        int i = index + 1;
        while (i < length) {
            char ch = data.charAt(i++);
            if (ch == '"') {
                return i;
            } else if (ch == '\\') {
                if (i == length) {
                    return -1;
                }
                char escaped = data.charAt(i++);
                if (escaped == 'u') {
                    if (i + 4 > length) {
                        return -1;
                    }
                    for (int end = i + 4; i < end; i++) {
                        if (Character.digit(data.charAt(i), 16) < 0) {
                            return -1;
                        }
                    }
                } else if ("\"\\/bfnrt".indexOf(escaped) < 0) {
                    return -1;
                }
            } else if (ch < 0x20) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Skips the Json number at the index: an optional minus, an integer part without leading zeros, an optional
     * fraction and an optional exponent. Returns the index after it, or -1 if it is not valid.
     */
    private static int skipNumber(String data, int index, int length) {
        int i = index;
        if (data.charAt(i) == '-') {
            i++;
        }
        if (i < length && data.charAt(i) == '0') {
            i++;
        } else {
            int start = i;
            i = skipDigits(data, i, length);
            if (i == start) {
                return -1;
            }
        }
        if (i < length && data.charAt(i) == '.') {
            int start = ++i;
            i = skipDigits(data, i, length);
            if (i == start) {
                return -1;
            }
        }
        if (i < length && (data.charAt(i) == 'e' || data.charAt(i) == 'E')) {
            i++;
            if (i < length && (data.charAt(i) == '+' || data.charAt(i) == '-')) {
                i++;
            }
            int start = i;
            i = skipDigits(data, i, length);
            if (i == start) {
                return -1;
            }
        }
        return i;
    }

    private static int skipDigits(String data, int index, int length) {
        while (index < length && data.charAt(index) >= '0' && data.charAt(index) <= '9') {
            index++;
        }
        return index;
    }
}
