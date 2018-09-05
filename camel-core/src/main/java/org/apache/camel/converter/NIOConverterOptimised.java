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
package org.apache.camel.converter;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.camel.Exchange;

/**
 * Optimised {@link NIOConverter}
 */
public final class NIOConverterOptimised {

    private NIOConverterOptimised() {
    }

    public static Object convertTo(final Class<?> type, final Exchange exchange, final Object value) throws Exception {
        Class fromType = value.getClass();

        if (type == String.class) {
            if (fromType == ByteBuffer.class) {
                return NIOConverter.toString((ByteBuffer) value, exchange);
            }
            return null;
        }

        if (type == byte[].class) {
            if (fromType == ByteBuffer.class) {
                return NIOConverter.toByteArray((ByteBuffer) value);
            }
            return null;
        }

        if (type == InputStream.class) {
            if (fromType == ByteBuffer.class) {
                return NIOConverter.toInputStream((ByteBuffer) value);
            }
            return null;
        }

        if (type == ByteBuffer.class) {
            if (fromType == byte[].class) {
                return NIOConverter.toByteBuffer((byte[]) value);
            } else if (fromType == File.class) {
                return NIOConverter.toByteBuffer((File) value);
            } else if (fromType == String.class) {
                return NIOConverter.toByteBuffer((String) value, exchange);
            } else if (fromType == short.class || fromType == Short.class) {
                return NIOConverter.toByteBuffer((Short) value);
            } else if (fromType == int.class || fromType == Integer.class) {
                return NIOConverter.toByteBuffer((Integer) value);
            } else if (fromType == long.class || fromType == Long.class) {
                return NIOConverter.toByteBuffer((Long) value);
            } else if (fromType == float.class || fromType == Float.class) {
                return NIOConverter.toByteBuffer((Float) value);
            } else if (fromType == double.class || fromType == Double.class) {
                return NIOConverter.toByteBuffer((Double) value);
            }
            return null;
        }

        // no optimised type converter found
        return null;
    }

}
