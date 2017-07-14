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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;

/**
 * Optimised {@link IOConverter}
 */
public final class IOConverterOptimised {

    private IOConverterOptimised() {
    }

    // CHECKSTYLE:OFF
    public static Object convertTo(final Class<?> type, final Exchange exchange, final Object value) throws Exception {
        Class fromType = value.getClass();

        // if the value is StreamCache then ensure its readable before doing conversions
        // by resetting it (this is also what StreamCachingAdvice does)
        if (value instanceof StreamCache) {
            ((StreamCache) value).reset();
        }

        if (type == InputStream.class) {
            if (fromType == String.class) {
                return IOConverter.toInputStream((String) value, exchange);
            } else if (fromType == URL.class) {
                return IOConverter.toInputStream((URL) value);
            } else if (fromType == File.class) {
                return IOConverter.toInputStream((File) value);
            } else if (fromType == byte[].class) {
                return IOConverter.toInputStream((byte[]) value);
            } else if (fromType == ByteArrayOutputStream.class) {
                return IOConverter.toInputStream((ByteArrayOutputStream) value);
            } else if (fromType == BufferedReader.class) {
                return IOConverter.toInputStream((BufferedReader) value, exchange);
            } else if (fromType == StringBuilder.class) {
                return IOConverter.toInputStream((StringBuilder) value, exchange);
            }
            return null;
        }

        if (type == Reader.class) {
            if (fromType == File.class) {
                return IOConverter.toReader((File) value, exchange);
            } else if (fromType == String.class) {
                return IOConverter.toReader((String) value);
            } else if (InputStream.class.isAssignableFrom(fromType)) {
                return IOConverter.toReader((InputStream) value, exchange);
            }
            return null;
        }

        if (type == File.class) {
            if (fromType == String.class) {
                return IOConverter.toFile((String) value);
            }
            return null;
        }

        if (type == OutputStream.class) {
            if (fromType == File.class) {
                return IOConverter.toOutputStream((File) value);
            }
            return null;
        }

        if (type == Writer.class) {
            if (fromType == File.class) {
                return IOConverter.toWriter((File) value, exchange);
            } else if (OutputStream.class.isAssignableFrom(fromType)) {
                return IOConverter.toWriter((OutputStream) value, exchange);
            }
            return null;
        }

        if (type == String.class) {
            if (fromType == byte[].class) {
                return IOConverter.toString((byte[]) value, exchange);
            } else if (fromType == File.class) {
                return IOConverter.toString((File) value, exchange);
            } else if (fromType == URL.class) {
                return IOConverter.toString((URL) value, exchange);
            } else if (fromType == BufferedReader.class) {
                return IOConverter.toString((BufferedReader) value);
            } else if (Reader.class.isAssignableFrom(fromType)) {
                return IOConverter.toString((Reader) value);
            } else if (InputStream.class.isAssignableFrom(fromType)) {
                return IOConverter.toString((InputStream) value, exchange);
            } else if (fromType == ByteArrayOutputStream.class) {
                return IOConverter.toString((ByteArrayOutputStream) value, exchange);
            }
            return null;
        }

        if (type == byte[].class) {
            if (fromType == BufferedReader.class) {
                return IOConverter.toByteArray((BufferedReader) value, exchange);
            } else if (Reader.class.isAssignableFrom(fromType)) {
                return IOConverter.toByteArray((Reader) value, exchange);
            } else if (fromType == File.class) {
                return IOConverter.toByteArray((File) value);
            } else if (fromType == String.class) {
                return IOConverter.toByteArray((String) value, exchange);
            } else if (fromType == ByteArrayOutputStream.class) {
                return IOConverter.toByteArray((ByteArrayOutputStream) value);
            } else if (InputStream.class.isAssignableFrom(fromType)) {
                return IOConverter.toBytes((InputStream) value);
            }
            return null;
        }

        if (type == ObjectInput.class) {
            if (fromType == InputStream.class || fromType == BufferedInputStream.class) {
                return IOConverter.toObjectInput((InputStream) value, exchange);
            }
            return null;
        }

        if (type == ObjectOutput.class) {
            if (fromType == OutputStream.class) {
                return IOConverter.toObjectOutput((OutputStream) value);
            }
            return null;
        }

        if (type == Properties.class) {
            if (fromType == File.class) {
                return IOConverter.toProperties((File) value);
            } else if (fromType == InputStream.class) {
                return IOConverter.toProperties((InputStream) value);
            } else if (fromType == Reader.class) {
                return IOConverter.toProperties((Reader) value);
            }
            return null;
        }

        // no optimised type converter found
        return null;
    }
    // CHECKSTYLE:ON

}
