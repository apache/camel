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
package org.apache.camel.component.aws2.sqs;

import java.nio.ByteBuffer;
import java.util.Date;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public final class Sqs2MessageHelper {

    public static final String TYPE_STRING = "String";
    public static final String TYPE_BINARY = "Binary";

    private Sqs2MessageHelper() {
    }

    public static MessageAttributeValue toMessageAttributeValue(Object value) {
        if (value instanceof String && !((String) value).isEmpty()) {
            MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
            mav.dataType(TYPE_STRING);
            mav.stringValue((String) value);
            return mav.build();
        } else if (value instanceof ByteBuffer) {
            MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
            mav.dataType(TYPE_BINARY);
            mav.binaryValue(SdkBytes.fromByteBuffer((ByteBuffer) value));
            return mav.build();
        } else if (value instanceof byte[]) {
            MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
            mav.dataType(TYPE_BINARY);
            mav.binaryValue(SdkBytes.fromByteArray((byte[]) value));
            return mav.build();
        } else if (value instanceof Boolean) {
            MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
            mav.dataType("Number.Boolean");
            mav.stringValue(Boolean.TRUE.equals(value) ? "1" : "0");
            return mav.build();
        } else if (value instanceof Number) {
            MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
            final String dataType;
            if (value instanceof Integer) {
                dataType = "Number.int";
            } else if (value instanceof Byte) {
                dataType = "Number.byte";
            } else if (value instanceof Double) {
                dataType = "Number.double";
            } else if (value instanceof Float) {
                dataType = "Number.float";
            } else if (value instanceof Long) {
                dataType = "Number.long";
            } else if (value instanceof Short) {
                dataType = "Number.short";
            } else {
                dataType = "Number";
            }
            mav.dataType(dataType);
            mav.stringValue(value.toString());
            return mav.build();
        } else if (value instanceof Date) {
            MessageAttributeValue.Builder mav = MessageAttributeValue.builder();
            mav.dataType(TYPE_STRING);
            mav.stringValue(value.toString());
            return mav.build();
        }

        return null;
    }

    public static Object fromMessageAttributeValue(MessageAttributeValue mav) {
        if (mav == null) {
            return null;
        }
        if (mav.binaryValue() != null) {
            return mav.binaryValue();
        } else if (mav.stringValue() != null) {
            String s = mav.stringValue();
            String dt = mav.dataType();
            if (dt == null || TYPE_STRING.equals(dt)) {
                return s;
            } else if ("Number.Boolean".equals(dt)) {
                return "1".equals(s) ? Boolean.TRUE : Boolean.FALSE;
            } else if ("Number.int".equals(dt)) {
                return Integer.valueOf(s);
            } else if ("Number.byte".equals(dt)) {
                return Byte.valueOf(s);
            } else if ("Number.double".equals(dt)) {
                return Double.valueOf(s);
            } else if ("Number.float".equals(dt)) {
                return Float.valueOf(s);
            } else if ("Number.long".equals(dt)) {
                return Long.valueOf(s);
            } else if ("Number.short".equals(dt)) {
                return Short.valueOf(s);
            }
            return s;
        }

        return null;
    }

}
