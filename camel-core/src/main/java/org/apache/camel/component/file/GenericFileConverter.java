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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * A set of converter methods for working with generic file types
 */
@Converter
public final class GenericFileConverter {

    private GenericFileConverter() {
        // Helper Class
    }

    @FallbackConverter
    public static Object convertTo(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
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
                return tc.convertTo(type, exchange, body);
            }
        }
        
        return null;
    }

    @Converter
    public static InputStream genericFileToInputStream(GenericFile<?> file, Exchange exchange) throws IOException, NoTypeConversionAvailableException {
        if (exchange != null) {
            if (file.getFile() instanceof java.io.File) {
                // prefer to use a file input stream if its a java.io.File (must use type converter to take care of encoding)
                File f = (File) file.getFile();
                InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, f);
                if (is != null) {
                    return is;
                }
            }
            // otherwise ensure the body is loaded as we want the input stream of the body
            file.getBinding().loadContent(exchange, file);
            return exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, file.getBody());
        } else {
            // should revert to fallback converter if we don't have an exchange
            return null;
        }
    }

    @Converter
    public static String genericFileToString(GenericFile<?> file, Exchange exchange) throws IOException {
        if (exchange != null) {
            // ensure the body is loaded as we do not want a toString of java.io.File handle returned, but the file content
            file.getBinding().loadContent(exchange, file);
            return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, file.getBody());
        } else {
            // should revert to fallback converter if we don't have an exchange
            return null;
        }
    }

    @Converter
    public static Serializable genericFileToSerializable(GenericFile<?> file, Exchange exchange) throws IOException {
        if (exchange != null) {
            // ensure the body is loaded as we do not want a java.io.File handle returned, but the file content
            file.getBinding().loadContent(exchange, file);
            return exchange.getContext().getTypeConverter().convertTo(Serializable.class, exchange, file.getBody());
        } else {
            // should revert to fallback converter if we don't have an exchange
            return null;
        }
    }
}
