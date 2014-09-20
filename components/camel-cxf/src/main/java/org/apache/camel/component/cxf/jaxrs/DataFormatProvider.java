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
package org.apache.camel.component.cxf.jaxrs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.apache.camel.spi.DataFormat;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;

public class DataFormatProvider<T> implements MessageBodyWriter<T>, MessageBodyReader<T> {

    private Map<String, DataFormat> formats = new HashMap<String, DataFormat>();

    @Override
    public boolean isReadable(Class<?> cls, Type type, Annotation[] anns, MediaType mt) {
        return getDataFormat(mt) != null;
    }

    @Override
    public boolean isWriteable(Class<?> cls, Type type, Annotation[] anns, MediaType mt) {
        return getDataFormat(mt) != null;
    }

    @Override
    public T readFrom(Class<T> cls, Type type, Annotation[] anns, MediaType mt,
                      MultivaluedMap<String, String> headers, InputStream is) throws IOException,
        WebApplicationException {
        DataFormat format = getValidDataFormat(mt);
        try {
            @SuppressWarnings("unchecked")
            T result = (T)format.unmarshal(null, is);
            return result;
        } catch (Exception ex) {
            throw new BadRequestException(ex);
        }
    }

    @Override
    public long getSize(T obj, Class<?> cls, Type type, Annotation[] anns, MediaType mt) {
        return -1;
    }

    @Override
    public void writeTo(T obj, Class<?> cls, Type type, Annotation[] anns, MediaType mt,
                        MultivaluedMap<String, Object> headers, OutputStream os) throws IOException,
        WebApplicationException {
        DataFormat format = getValidDataFormat(mt);
        try {
            format.marshal(null, obj, os);
        } catch (Exception ex) {
            throw new InternalServerErrorException(ex);
        }

    }

    public void setFormat(DataFormat format) {
        setFormat(MediaType.WILDCARD, format);
    }

    public void setFormat(String mediaType, DataFormat format) {
        formats.put(mediaType, format);
    }

    public void setFormats(Map<String, DataFormat> formats) {
        this.formats.putAll(formats);
    }

    private DataFormat getValidDataFormat(MediaType mt) {
        DataFormat format = getDataFormat(mt);
        if (format == null) {
            throw new InternalServerErrorException();
        }
        return format;
    }

    private DataFormat getDataFormat(MediaType mt) {
        String type = JAXRSUtils.mediaTypeToString(mt);
        DataFormat format = formats.get(type);
        if (format != null) {
            return format;
        }
        int subtypeIndex = type.lastIndexOf('+');
        if (subtypeIndex != -1) {
            // example, application/json+v1, should still be handled by JSON
            // handler, etc
            format = formats.get(type.substring(0, subtypeIndex));
        }
        if (format == null && formats.containsKey(MediaType.WILDCARD)) {
            format = formats.get(MediaType.WILDCARD);
        }
        return format;
    }
}
