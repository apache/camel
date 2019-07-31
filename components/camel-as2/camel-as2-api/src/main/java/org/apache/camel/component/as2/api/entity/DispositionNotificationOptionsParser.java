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
package org.apache.camel.component.as2.api.entity;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.as2.api.util.AS2HeaderUtils;
import org.apache.camel.component.as2.api.util.AS2HeaderUtils.Parameter;
import org.apache.http.ParseException;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

public class DispositionNotificationOptionsParser {

    public static final DispositionNotificationOptionsParser INSTANCE = new DispositionNotificationOptionsParser();

    private static final String SIGNED_RECEIPT_PROTOCOL_ATTR_NAME = "signed-receipt-protocol";
    private static final String SIGNED_RECEIPT_MICALG_ATTR_NAME = "signed-receipt-micalg";

    public static DispositionNotificationOptions parseDispositionNotificationOptions(final String value,
                                                                                     DispositionNotificationOptionsParser parser)
            throws ParseException {
        if (value == null) {
            return new DispositionNotificationOptions(null, null);
        }

        final CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        final ParserCursor cursor = new ParserCursor(0, value.length());
        return (parser != null ? parser : DispositionNotificationOptionsParser.INSTANCE)
                .parseDispositionNotificationOptions(buffer, cursor);
    }

    public DispositionNotificationOptions parseDispositionNotificationOptions(final CharArrayBuffer buffer,
                                                                              final ParserCursor cursor) {
        Args.notNull(buffer, "buffer");
        Args.notNull(cursor, "cursor");

        Map<String, Parameter> parameters = new HashMap<>();
        while (!cursor.atEnd()) {
            Parameter parameter = AS2HeaderUtils.parseParameter(buffer, cursor);
            parameters.put(parameter.getAttribute(), parameter);
        }

        Parameter signedReceiptProtocolParameter = parameters.get(SIGNED_RECEIPT_PROTOCOL_ATTR_NAME);

        Parameter signedReceiptMicalgParameter = parameters.get(SIGNED_RECEIPT_MICALG_ATTR_NAME);


        return new DispositionNotificationOptions(signedReceiptProtocolParameter, signedReceiptMicalgParameter);
    }

}
