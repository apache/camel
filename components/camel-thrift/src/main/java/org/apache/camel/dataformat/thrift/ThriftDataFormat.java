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
package org.apache.camel.dataformat.thrift;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.io.IOUtils;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TSimpleJSONProtocol;

@Dataformat("thrift")
public class ThriftDataFormat extends ServiceSupport
        implements DataFormat, DataFormatName, DataFormatContentTypeHeader, CamelContextAware {

    public static final String CONTENT_TYPE_FORMAT_BINARY = "binary";
    public static final String CONTENT_TYPE_FORMAT_JSON = "json";
    public static final String CONTENT_TYPE_FORMAT_SIMPLE_JSON = "sjson";

    private static final String CONTENT_TYPE_HEADER_NATIVE = "application/octet-stream";
    private static final String CONTENT_TYPE_HEADER_JSON = "application/json";

    private CamelContext camelContext;
    @SuppressWarnings("rawtypes")
    private TBase defaultInstance;
    private String instanceClassName;
    private boolean contentTypeHeader = true;
    private String contentTypeFormat = CONTENT_TYPE_FORMAT_BINARY;

    public ThriftDataFormat() {
    }

    @SuppressWarnings("rawtypes")
    public ThriftDataFormat(TBase defaultInstance) {
        this.defaultInstance = defaultInstance;
    }

    @SuppressWarnings("rawtypes")
    public ThriftDataFormat(TBase defaultInstance, String contentTypeFormat) {
        this.defaultInstance = defaultInstance;
        this.contentTypeFormat = contentTypeFormat;
    }

    @Override
    public String getDataFormatName() {
        return "thrift";
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @SuppressWarnings("rawtypes")
    public void setDefaultInstance(TBase instance) {
        this.defaultInstance = instance;
    }

    @SuppressWarnings("rawtypes")
    public void setDefaultInstance(Object instance) {
        if (instance instanceof TBase) {
            this.defaultInstance = (TBase) instance;
        } else {
            throw new IllegalArgumentException(
                    "The argument for setDefaultInstance should be subClass of org.apache.thrift.TBase");
        }
    }

    public void setInstanceClass(String className) {
        ObjectHelper.notNull(className, "ThriftDataFormat instaceClass");
        instanceClassName = className;
    }

    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /*
     * Defines a content type format in which thrift message will be
     * serialized/deserialized from(to) the Java been. It can be native thrift
     * format or JSON fields representation. The default value is 'native'.
     */
    public void setContentTypeFormat(String contentTypeFormat) {
        StringHelper.notEmpty(contentTypeFormat, "ThriftDataFormat contentTypeFormat");
        this.contentTypeFormat = contentTypeFormat;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.camel.spi.DataFormat#marshal(org.apache.camel.Exchange,
     * java.lang.Object, java.io.OutputStream)
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void marshal(final Exchange exchange, final Object graph, final OutputStream outputStream) throws Exception {
        String contentTypeHeader = CONTENT_TYPE_HEADER_NATIVE;
        TSerializer serializer;

        if (contentTypeFormat.equals(CONTENT_TYPE_FORMAT_JSON)) {
            serializer = new TSerializer(new TJSONProtocol.Factory());
            IOUtils.write(serializer.toString((TBase) graph), outputStream, StandardCharsets.UTF_8);
            contentTypeHeader = CONTENT_TYPE_HEADER_JSON;
        } else if (contentTypeFormat.equals(CONTENT_TYPE_FORMAT_SIMPLE_JSON)) {
            serializer = new TSerializer(new TSimpleJSONProtocol.Factory());
            IOUtils.write(serializer.toString((TBase) graph), outputStream, StandardCharsets.UTF_8);
            contentTypeHeader = CONTENT_TYPE_HEADER_JSON;
        } else if (contentTypeFormat.equals(CONTENT_TYPE_FORMAT_BINARY)) {
            serializer = new TSerializer(new TBinaryProtocol.Factory());
            IOUtils.write(serializer.serialize((TBase) graph), outputStream);
        } else {
            throw new CamelException("Invalid thrift content type format: " + contentTypeFormat);
        }

        if (isContentTypeHeader()) {
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, contentTypeHeader);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.camel.spi.DataFormat#unmarshal(org.apache.camel.Exchange,
     * java.io.InputStream)
     */
    @Override
    public Object unmarshal(final Exchange exchange, final InputStream inputStream) throws Exception {
        TDeserializer deserializer;
        ObjectHelper.notNull(defaultInstance, "defaultInstance or instanceClassName must be set", this);

        if (contentTypeFormat.equals(CONTENT_TYPE_FORMAT_JSON)) {
            deserializer = new TDeserializer(new TJSONProtocol.Factory());
            deserializer.deserialize(defaultInstance, IOUtils.toByteArray(inputStream));
        } else if (contentTypeFormat.equals(CONTENT_TYPE_FORMAT_BINARY)) {
            deserializer = new TDeserializer(new TBinaryProtocol.Factory());
            deserializer.deserialize(defaultInstance, IOUtils.toByteArray(inputStream));
        } else if (contentTypeFormat.equals(CONTENT_TYPE_FORMAT_SIMPLE_JSON)) {
            throw new CamelException("Simple JSON format is avalable for the message marshalling only");
        } else {
            throw new CamelException("Invalid thrift content type format: " + contentTypeFormat);
        }

        return defaultInstance;
    }

    @SuppressWarnings("rawtypes")
    protected TBase loadDefaultInstance(final String className, final CamelContext context)
            throws CamelException, ClassNotFoundException {
        Class<?> instanceClass = context.getClassResolver().resolveMandatoryClass(className);
        if (TBase.class.isAssignableFrom(instanceClass)) {
            try {
                return (TBase) instanceClass.getDeclaredConstructor().newInstance();
            } catch (final Exception ex) {
                throw new CamelException(
                        "Cannot set the defaultInstance of ThriftDataFormat with " + className + ", caused by " + ex);
            }
        } else {
            throw new CamelException(
                    "Cannot set the defaultInstance of ThriftDataFormat with " + className
                                     + ", as the class is not a subClass of org.apache.thrift.TBase");
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (defaultInstance == null && instanceClassName != null) {
            defaultInstance = loadDefaultInstance(instanceClassName, getCamelContext());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
