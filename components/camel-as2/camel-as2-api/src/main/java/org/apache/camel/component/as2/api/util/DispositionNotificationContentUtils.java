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
package org.apache.camel.component.as2.api.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.as2.api.MDNField;
import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;
import org.apache.camel.component.as2.api.entity.AS2DispositionType;
import org.apache.camel.component.as2.api.entity.AS2MessageDispositionNotificationEntity;
import org.apache.camel.component.as2.api.entity.DispositionMode;
import org.apache.camel.component.as2.api.util.DispositionNotificationContentUtils.Field.Element;
import org.apache.camel.component.as2.api.util.MicUtils.ReceivedContentMic;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.ParseException;
import org.apache.http.message.ParserCursor;
import org.apache.http.message.TokenParser;
import org.apache.http.util.CharArrayBuffer;

public final class DispositionNotificationContentUtils {

    private static final String REPORTING_UA = "reporting-ua";
    private static final String MDN_GATEWAY = "mdn-gateway";
    private static final String FINAL_RECIPIENT = "final-recipient";
    private static final String ORIGINAL_MESSAGE_ID = "original-message-id";
    private static final String DISPOSITION = "disposition";
    private static final String FAILURE = "failure";
    private static final String ERROR = "error";
    private static final String WARNING = "warning";
    private static final String RECEIVED_CONTENT_MIC = "received-content-mic";

    public static class Field {

        public static class Element {

            private final String value;
            private final String[] parameters;

            public Element(String value, String[] parameters) {
                this.value = value;
                this.parameters = (parameters == null) ? new String[] {} : parameters;
            }

            public String getValue() {
                return value;
            }

            public String[] getParameters() {
                return parameters;
            }

            @Override
            public String toString() {
                return value + ((parameters.length > 0) ? ", " + String.join(",", parameters) : "");
            }

        }

        private String name;
        private Element[] elements;

        public Field(String name, Element[] elements) {
            this.name = ObjectHelper.notNull(name, "name");
            this.elements = (elements == null) ? new Element[] {} : elements;
        }

        public Field(String name, String value) {
            this.name = ObjectHelper.notNull(name, "name");
            this.elements = new Element[] { new Element(value, null) };
        }

        public String getName() {
            return name;
        }

        public Element[] getElements() {
            return elements;
        }

        public String getValue() {

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < elements.length; i++) {
                Element element = elements[i];
                if (i > 0) {
                    builder.append("; ").append(element);
                } else {
                    builder.append(element);
                }
            }

            return builder.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(": ");
            for (int i = 0; i < elements.length; i++) {
                Element element = elements[i];
                if (i > 0) {
                    sb.append("; ").append(element);
                } else {
                    sb.append(element);
                }
            }
            return sb.toString();
        }

    }

    private static final TokenParser TOKEN_PARSER = TokenParser.INSTANCE;

    private static final char PARAM_DELIMITER = ',';
    private static final char ELEM_DELIMITER = ';';

    private static final BitSet TOKEN_DELIMS = TokenParser.INIT_BITSET(PARAM_DELIMITER, ELEM_DELIMITER);

    private DispositionNotificationContentUtils() {
    }

    public static AS2MessageDispositionNotificationEntity parseDispositionNotification(
            List<CharArrayBuffer> dispositionNotificationFields)
            throws ParseException {
        String reportingUA = null;
        String mtaName = null;
        String finalRecipient = null;
        String originalMessageId = null;
        DispositionMode dispositionMode = null;
        AS2DispositionType dispositionType = null;
        AS2DispositionModifier dispositionModifier = null;
        List<String> failures = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> extensionFields = new HashMap<>();
        ReceivedContentMic receivedContentMic = null;

        for (int i = 0; i < dispositionNotificationFields.size(); i++) {
            final CharArrayBuffer fieldLine = dispositionNotificationFields.get(i);
            final Field field = parseDispositionField(fieldLine);
            switch (field.getName().toLowerCase()) {
                case REPORTING_UA: {
                    if (field.getElements().length < 1) {
                        throw new ParseException("Invalid '" + MDNField.REPORTING_UA + "' field: UA name is missing");
                    }
                    reportingUA = field.getValue();
                    break;
                }
                case MDN_GATEWAY: {
                    Element[] elements = field.getElements();
                    if (elements.length < 2) {
                        throw new ParseException("Invalid '" + MDNField.MDN_GATEWAY + "' field: MTA name is missing");
                    }
                    mtaName = elements[1].getValue();
                    break;
                }
                case FINAL_RECIPIENT: {
                    Element[] elements = field.getElements();
                    if (elements.length < 2) {
                        throw new ParseException(
                                "Invalid '" + MDNField.FINAL_RECIPIENT + "' field: recipient address is missing");
                    }
                    finalRecipient = elements[1].getValue();
                    break;
                }
                case ORIGINAL_MESSAGE_ID: {
                    originalMessageId = field.getValue();
                    break;
                }
                case DISPOSITION: {
                    Element[] elements = field.getElements();
                    if (elements.length < 2) {
                        throw new ParseException("Invalid '" + MDNField.DISPOSITION + "' field: " + field.getValue());
                    }
                    dispositionMode = DispositionMode.parseDispositionMode(elements[0].getValue());
                    if (dispositionMode == null) {
                        throw new ParseException(
                                "Invalid '" + MDNField.DISPOSITION + "' field: invalid disposition mode '"
                                                 + elements[0].getValue() + "'");
                    }

                    String dispositionTypeString = elements[1].getValue();
                    int slash = dispositionTypeString.indexOf("/");
                    if (slash == -1) {
                        dispositionType = AS2DispositionType.parseDispositionType(dispositionTypeString);
                    } else {
                        dispositionType = AS2DispositionType.parseDispositionType(dispositionTypeString.substring(0, slash));
                        dispositionModifier
                                = AS2DispositionModifier.parseDispositionType(dispositionTypeString.substring(slash + 1));
                    }
                    break;
                }
                case FAILURE:
                    failures.add(field.getValue());
                    break;
                case ERROR:
                    errors.add(field.getValue());
                    break;
                case WARNING:
                    warnings.add(field.getValue());
                    break;
                case RECEIVED_CONTENT_MIC: {
                    Element[] elements = field.getElements();
                    if (elements.length < 1) {
                        throw new ParseException("Invalid '" + MDNField.RECEIVED_CONTENT_MIC + "' field: MIC is missing");
                    }
                    Element element = elements[0];
                    String[] parameters = element.getParameters();
                    if (parameters.length < 1) {
                        throw new ParseException(
                                "Invalid '" + MDNField.RECEIVED_CONTENT_MIC + "' field: digest algorithm ID is missing");
                    }
                    String digestAlgorithmId = parameters[0];
                    String encodedMessageDigest = element.getValue();
                    receivedContentMic = new ReceivedContentMic(digestAlgorithmId, encodedMessageDigest);
                    break;
                }
                default: // Extension Field
                    extensionFields.put(field.getName(), field.getValue());
            }
        }

        return new AS2MessageDispositionNotificationEntity(
                reportingUA,
                mtaName,
                finalRecipient,
                originalMessageId,
                dispositionMode,
                dispositionType,
                dispositionModifier,
                failures.toArray(new String[0]),
                errors.toArray(new String[0]),
                warnings.toArray(new String[0]),
                extensionFields,
                receivedContentMic);
    }

    public static Field parseDispositionField(CharArrayBuffer fieldLine) {
        final int colon = fieldLine.indexOf(':');
        if (colon == -1) {
            throw new ParseException("Invalid field: " + fieldLine.toString());
        }
        final String fieldName = fieldLine.substringTrimmed(0, colon);

        ParserCursor cursor = new ParserCursor(colon + 1, fieldLine.length());

        final List<Element> elements = new ArrayList<>();
        while (!cursor.atEnd()) {
            final Element element = parseDispositionFieldElement(fieldLine, cursor);
            if (element.getValue() != null) {
                elements.add(element);
            }
        }

        return new Field(fieldName, elements.toArray(new Element[0]));
    }

    public static Element parseDispositionFieldElement(CharArrayBuffer fieldLine, ParserCursor cursor) {

        final String value = TOKEN_PARSER.parseToken(fieldLine, cursor, TOKEN_DELIMS);
        if (cursor.atEnd()) {
            return new Element(value, null);
        }

        final char delim = fieldLine.charAt(cursor.getPos());
        cursor.updatePos(cursor.getPos() + 1);
        if (delim == ELEM_DELIMITER) {
            return new Element(value, null);
        }

        final List<String> parameters = new ArrayList<>();
        while (!cursor.atEnd()) {
            final String parameter = TOKEN_PARSER.parseToken(fieldLine, cursor, TOKEN_DELIMS);
            parameters.add(parameter);
            if (cursor.atEnd()) {
                break;
            }
            final char ch = fieldLine.charAt(cursor.getPos());
            if (!cursor.atEnd()) {
                cursor.updatePos(cursor.getPos() + 1);
            }
            if (ch == ELEM_DELIMITER) {
                break;
            }
        }

        return new Element(value, parameters.toArray(new String[0]));
    }
}
