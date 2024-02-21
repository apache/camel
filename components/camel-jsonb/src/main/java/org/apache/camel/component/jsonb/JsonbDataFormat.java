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
package org.apache.camel.component.jsonb;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Type;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyOrderStrategy;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Marshal POJOs to JSON and back using JSON-B.
 */
@Dataformat("jsonb")
@Metadata(includeProperties = "unmarshalTypeName,unmarshalType,objectMapper,prettyPrint,binaryStrategy,encoding,propertyOrder,propertyNamingStrategy,skipNull")
public class JsonbDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {
    private CamelContext camelContext;
    private Jsonb objectMapper;
    private String unmarshalTypeName;
    private Class<?> unmarshalType;
    private Type customType;
    private boolean prettyPrint;
    private String encoding = "UTF-8";
    private String binaryStrategy = BinaryDataStrategy.BASE_64;
    private String propertyOrder = PropertyOrderStrategy.ANY;
    private String propertyNamingStrategy = PropertyNamingStrategy.IDENTITY;
    private boolean skipNull = true;

    public JsonbDataFormat() {
        this(Object.class);
    }

    /**
     * Use the default JSON-B {@link Jsonb} and with a custom unmarshal type
     *
     * @param customType the custom unmarshal type
     */
    public JsonbDataFormat(Type customType) {
        this.customType = customType;
    }

    public JsonbDataFormat(Class<?> unmarshalType) {
        this(null, unmarshalType);
    }

    public JsonbDataFormat(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    /**
     * Use a custom JSON-B instance and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JsonbDataFormat(Jsonb mapper, Class<?> unmarshalType) {
        this.objectMapper = mapper;
        this.unmarshalType = unmarshalType;
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
        return "jsonb";
    }

    public Jsonb getObjectMapper() {
        return objectMapper;
    }

    /**
     * Set a custom Jsonb instance, potentially initialized with a custom JsonbConfig.
     *
     * @param objectMapper the Jsonb instance to set.
     */
    public void setObjectMapper(Jsonb objectMapper) {
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

    public boolean isSkipNull() {
        return skipNull;
    }

    public void setSkipNull(boolean skipNull) {
        this.skipNull = skipNull;
    }

    public String getBinaryStrategy() {
        return binaryStrategy;
    }

    public void setBinaryStrategy(String binaryStrategy) {
        this.binaryStrategy = binaryStrategy;
    }

    public String getPropertyOrder() {
        return propertyOrder;
    }

    public void setPropertyOrder(String propertyOrder) {
        this.propertyOrder = propertyOrder;
    }

    public String getPropertyNamingStrategy() {
        return propertyNamingStrategy;
    }

    public void setPropertyNamingStrategy(String propertyNamingStrategy) {
        this.propertyNamingStrategy = propertyNamingStrategy;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) {
        objectMapper.toJson(graph, stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return unmarshal(exchange, (Object) stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, Object body) throws Exception {
        // is there a header with the unmarshal type?
        Class<?> expectedType = unmarshalType;
        String type = exchange.getIn().getHeader("CamelJsonbUnmarshallType", String.class);
        if (type != null) {
            expectedType = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
        }
        if (expectedType == null && customType != null) {
            if (body instanceof String str) {
                return objectMapper.fromJson(str, customType);
            } else if (body instanceof Reader r) {
                return objectMapper.fromJson(r, customType);
            } else {
                // fallback to input stream
                InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
                return objectMapper.fromJson(is, customType);
            }
        } else {
            if (body instanceof String str) {
                return objectMapper.fromJson(str, expectedType);
            } else if (body instanceof Reader r) {
                return objectMapper.fromJson(r, expectedType);
            } else {
                // fallback to input stream
                InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
                return objectMapper.fromJson(is, expectedType);
            }
        }
    }

    @Override
    protected void doInit() {
        if (unmarshalTypeName != null && (unmarshalType == null || unmarshalType == Object.class)) {
            unmarshalType = camelContext.getClassResolver().resolveClass(unmarshalTypeName);
        }
    }

    @Override
    protected void doStart() {
        if (objectMapper == null) {
            objectMapper = JsonbBuilder.create(new JsonbConfig()
                    .withFormatting(prettyPrint)
                    .withNullValues(!skipNull)
                    .withBinaryDataStrategy(binaryStrategy)
                    .withPropertyOrderStrategy(propertyOrder)
                    .withPropertyNamingStrategy(propertyNamingStrategy)
                    .withEncoding(encoding));
        }
    }

    @Override
    protected void doStop() {
        // noop
    }
}
