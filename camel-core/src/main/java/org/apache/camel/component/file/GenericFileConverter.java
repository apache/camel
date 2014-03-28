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
package org.apache.camel.component.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set of converter methods for working with generic file types
 */
@Converter
public final class GenericFileConverter {

    private static final Logger LOG = LoggerFactory.getLogger(GenericFileConverter.class);

    private GenericFileConverter() {
        // Helper Class
    }

    @FallbackConverter
    public static Object convertTo(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry)
        throws IOException, NoTypeConversionAvailableException {

        // use a fallback type converter so we can convert the embedded body if the value is GenericFile
        if (GenericFile.class.isAssignableFrom(value.getClass())) {

            GenericFile<?> file = (GenericFile<?>) value;
            Class<?> from = file.getBody().getClass();

            // maybe from is already the type we want
            if (from.isAssignableFrom(type)) {
                return file.getBody();
            }

            // no then try to lookup a type converter
            TypeConverter tc = registry.lookup(type, from);
            if (tc != null) {
                Object body = file.getBody();
                // if its a file and we have a charset then use a reader to ensure we read the content using the given charset
                // this is a bit complicated, but a file consumer can be configured with an explicit charset, which means
                // we should read the file content with that given charset, and ignore any other charset properties

                // if the desired type is InputStream or Reader we can use the optimized methods
                if (Reader.class.isAssignableFrom(type)) {
                    Reader reader = genericFileToReader(file, exchange);
                    if (reader != null) {
                        return reader;
                    }
                }
                if (InputStream.class.isAssignableFrom(type)) {
                    InputStream is = genericFileToInputStream(file, exchange);
                    if (is != null) {
                        return is;
                    }
                }

                // okay if the file has a charset configured then we must try to load the file using that charset
                // which mean we have to use the Reader first, and then convert from there
                if (body instanceof File && file.getCharset() != null) {
                    Reader reader = genericFileToReader(file, exchange);
                    // we dont want a reader back, so use the type converter registry to find a suitable converter
                    TypeConverter readerTc = registry.lookup(type, Reader.class);
                    if (readerTc != null) {
                        // use the reader based type converter
                        return readerTc.convertTo(type, exchange, reader);
                    }
                }
                // fallback and use the type suitable type converter
                return tc.convertTo(type, exchange, body);
            }
        }
        
        return null;
    }

    @Converter
    public static InputStream genericFileToInputStream(GenericFile<?> file, Exchange exchange) throws IOException, NoTypeConversionAvailableException {
        if (file.getFile() instanceof File) {
            // prefer to use a file input stream if its a java.io.File
            File f = (File) file.getFile();
            // the file must exists
            if (f.exists()) {
                // read the file using the specified charset
                String charset = file.getCharset();
                if (charset != null) {
                    LOG.debug("Read file {} with charset {}", f, file.getCharset());
                } else {
                    LOG.debug("Read file {} (no charset)", f);
                }
                return IOConverter.toInputStream(f, charset);
            }
        }
        if (exchange != null) {
            // otherwise ensure the body is loaded as we want the input stream of the body
            file.getBinding().loadContent(exchange, file);
            return exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, file.getBody());
        } else {
            // should revert to fallback converter if we don't have an exchange
            return null;
        }
    }

    @Converter
    public static String genericFileToString(GenericFile<?> file, Exchange exchange) throws IOException, NoTypeConversionAvailableException {
        // use reader first as it supports the file charset
        BufferedReader reader = genericFileToReader(file, exchange);
        if (reader != null) {
            return IOConverter.toString(reader);
        }
        if (exchange != null) {
            // otherwise ensure the body is loaded as we want the content of the body
            file.getBinding().loadContent(exchange, file);
            return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, file.getBody());
        } else {
            // should revert to fallback converter if we don't have an exchange
            return null;
        }
    }

    @Converter
    public static Serializable genericFileToSerializable(GenericFile<?> file, Exchange exchange) throws IOException, NoTypeConversionAvailableException {
        if (exchange != null) {
            // load the file using input stream
            InputStream is = genericFileToInputStream(file, exchange);
            if (is != null) {
                // need to double convert to convert correctly
                byte[] data = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, is);
                if (data != null) {
                    return exchange.getContext().getTypeConverter().convertTo(Serializable.class, exchange, data);
                }
            }
        }
        // should revert to fallback converter if we don't have an exchange
        return null;
    }

    private static BufferedReader genericFileToReader(GenericFile<?> file, Exchange exchange) throws IOException, NoTypeConversionAvailableException {
        if (file.getFile() instanceof File) {
            // prefer to use a file input stream if its a java.io.File
            File f = (File) file.getFile();
            // the file must exists
            if (!f.exists()) {
                return null;
            }
            // and use the charset if the file was explicit configured with a charset
            String charset = file.getCharset();
            if (charset != null) {
                LOG.debug("Read file {} with charset {}", f, file.getCharset());
                return IOConverter.toReader(f, charset);
            } else {
                LOG.debug("Read file {} (no charset)", f);
                return IOConverter.toReader(f, exchange);
            }
        }
        return null;
    }
}
