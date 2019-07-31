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
import java.util.List;

import org.apache.camel.component.as2.api.entity.Importance;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.ParserCursor;
import org.apache.http.message.TokenParser;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

public final class AS2HeaderUtils {

    public static class Parameter {
        private final String attribute;
        private final Importance importance;
        private final String[] values;

        public Parameter(String attribute, String importance, String[] values) {
            this.attribute = Args.notNull(attribute, "attribute");
            this.importance = Importance.get(importance);
            this.values = values;
        }

        public String getAttribute() {
            return attribute;
        }

        public Importance getImportance() {
            return importance;
        }

        public String[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(attribute);
            if (importance != null) {
                sb.append("=" + importance.toString());
            }
            if (values != null) {
                for (String value : values) {
                    sb.append("," + value);
                }
            }
            return sb.toString();
        }
    }

    private static final char PARAM_DELIMITER = ',';
    private static final char ELEM_DELIMITER = ';';
    private static final char NAME_VALUE_DELIMITER = '=';

    private static final TokenParser TOKEN_PARSER = TokenParser.INSTANCE;

    private static final BitSet TOKEN_DELIMS = TokenParser.INIT_BITSET(NAME_VALUE_DELIMITER, PARAM_DELIMITER,
            ELEM_DELIMITER);
    private static final BitSet VALUE_DELIMS = TokenParser.INIT_BITSET(PARAM_DELIMITER, ELEM_DELIMITER);

    private AS2HeaderUtils() {
    }

    public static Header createHeader(String headerName, String[]... elements) {
        StringBuilder sb = new StringBuilder();

        boolean firstElement = true;
        for (String[] element: elements) {
            if (element.length == 0) {
                continue;
            }
            if (firstElement) {
                firstElement = false;
            } else {
                sb.append(ELEM_DELIMITER);
            }
            sb.append(element[0]);
            if (element.length > 1) {
                sb.append(NAME_VALUE_DELIMITER + element[1]);
            }
        }
        BasicHeader header = new BasicHeader(headerName, sb.toString());
        return header;
    }

    public static Parameter parseParameter(final CharArrayBuffer buffer, final ParserCursor cursor) {
        Args.notNull(buffer, "Char array buffer");
        Args.notNull(cursor, "Parser cursor");

        final String name = TOKEN_PARSER.parseToken(buffer, cursor, TOKEN_DELIMS);
        if (cursor.atEnd()) {
            return new Parameter(name, null, null);
        }

        final int delim = buffer.charAt(cursor.getPos());
        cursor.updatePos(cursor.getPos() + 1);
        if (delim != NAME_VALUE_DELIMITER) {
            return new Parameter(name, null, null);
        }

        final String importance = TOKEN_PARSER.parseValue(buffer, cursor, VALUE_DELIMS);
        if (!cursor.atEnd()) {
            cursor.updatePos(cursor.getPos() + 1);
        }

        List<String> values = new ArrayList<>();
        while (!cursor.atEnd()) {
            String value = TOKEN_PARSER.parseValue(buffer, cursor, VALUE_DELIMS);
            values.add(value);
            if (cursor.atEnd()) {
                break;
            }
            final int delimiter = buffer.charAt(cursor.getPos());
            if (!cursor.atEnd()) {
                cursor.updatePos(cursor.getPos() + 1);
            }
            if (delimiter == ELEM_DELIMITER) {
                break;
            }
        }

        return new Parameter(name, importance, values.toArray(new String[values.size()]));
    }

    public static String getParameterValue(Header[] headers, String headerName, String parameterName) {
        Args.notNull(headers, "headers");
        Args.notNull(headerName, "headerName");
        for (Header header : headers) {
            if (header.getName().equalsIgnoreCase(headerName)) {
                for (HeaderElement headerElement : header.getElements()) {
                    for (NameValuePair nameValuePair : headerElement.getParameters()) {
                        if (nameValuePair.getName().equalsIgnoreCase(parameterName)) {
                            return nameValuePair.getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

}
