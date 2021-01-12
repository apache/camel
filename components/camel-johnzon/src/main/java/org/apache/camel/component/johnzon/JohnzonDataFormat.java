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
package org.apache.camel.component.johnzon;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.johnzon.mapper.Mapper;
import org.apache.johnzon.mapper.MapperBuilder;
import org.apache.johnzon.mapper.reflection.JohnzonParameterizedType;

/**
 * Marshal POJOs to JSON and back using <a href="http://johnzon.apache.org/">Johnzon</a>
 */
@Dataformat("json-johnzon")
@Metadata(includeProperties = "unmarshalTypeName,objectMapper,prettyPrint")
public class JohnzonDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private CamelContext camelContext;
    private Mapper objectMapper;
    private String unmarshalTypeName;
    private Class<?> unmarshalType;
    private JohnzonParameterizedType parameterizedType;
    private Comparator<String> attributeOrder;
    private boolean prettyPrint;
    private String encoding;
    private boolean skipEmptyArray;
    private boolean skipNull;

    public JohnzonDataFormat() {
        this(Object.class);
    }

    /**
     * Use the default Johnzon {@link Mapper} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JohnzonDataFormat(Class<?> unmarshalType) {
        this(null, unmarshalType);
    }

    /**
     * Use the default Johnzon {@link Mapper} and with a custom parameterized type
     *
     * @param parameterizedType the custom parameterized type
     */
    public JohnzonDataFormat(JohnzonParameterizedType parameterizedType) {
        this(null, parameterizedType);
    }

    /**
     * Use a custom Johnzon mapper and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JohnzonDataFormat(Mapper mapper, Class<?> unmarshalType) {
        this.objectMapper = mapper;
        this.unmarshalType = unmarshalType;
    }

    /**
     * Use a custom Johnzon mapper and unmarshal type
     *
     * @param mapper            the custom mapper
     * @param parameterizedType the JohnzonParameterizedType type
     */
    public JohnzonDataFormat(Mapper mapper, JohnzonParameterizedType parameterizedType) {
        this.objectMapper = mapper;
        this.parameterizedType = parameterizedType;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public String getDataFormatName() {
        return "json-johnzon";
    }

    public Mapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(Mapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public JohnzonParameterizedType getParameterizedType() {
        return parameterizedType;
    }

    public void setParameterizedType(JohnzonParameterizedType parameterizedType) {
        this.parameterizedType = parameterizedType;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean isSkipEmptyArray() {
        return skipEmptyArray;
    }

    public void setSkipEmptyArray(boolean skipEmptyArray) {
        this.skipEmptyArray = skipEmptyArray;
    }

    public boolean isSkipNull() {
        return skipNull;
    }

    public void setSkipNull(boolean skipNull) {
        this.skipNull = skipNull;
    }

    public Comparator<String> getAttributeOrder() {
        return attributeOrder;
    }

    public void setAttributeOrder(Comparator<String> attributeOrder) {
        this.attributeOrder = attributeOrder;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        objectMapper.writeObject(graph, stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        // is there a header with the unmarshal type?
        Class<?> clazz = unmarshalType;
        String type = exchange.getIn().getHeader(JohnzonConstants.UNMARSHAL_TYPE, String.class);
        if (type != null) {
            clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
        }
        if (parameterizedType != null) {
            return this.objectMapper.readCollection(stream, parameterizedType);
        } else {
            return this.objectMapper.readObject(stream, clazz);
        }
    }

    @Override
    protected void doInit() throws Exception {
        if (unmarshalTypeName != null && (unmarshalType == null || unmarshalType == Object.class)) {
            unmarshalType = camelContext.getClassResolver().resolveClass(unmarshalTypeName);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (objectMapper == null) {
            MapperBuilder builder = new MapperBuilder();
            builder.setPretty(prettyPrint);
            builder.setSkipNull(skipNull);
            builder.setSkipEmptyArray(skipEmptyArray);
            if (ObjectHelper.isNotEmpty(encoding)) {
                builder.setEncoding(encoding);
            }
            if (ObjectHelper.isNotEmpty(attributeOrder)) {
                builder.setAttributeOrder(attributeOrder);
            }
            objectMapper = builder.build();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
