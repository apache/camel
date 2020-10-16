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
import java.lang.reflect.Type;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.config.BinaryDataStrategy;
import javax.json.bind.config.PropertyNamingStrategy;
import javax.json.bind.config.PropertyOrderStrategy;

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
@Dataformat("json-jsonb")
@Metadata(includeProperties = "unmarshalTypeName,objectMapper,prettyPrint,binaryStrategy,encoding,propertyOrder,propertyamingStrategy,skipNull")
public class JsonbDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {
    private CamelContext camelContext;
    private Jsonb objectMapper;
    private String unmarshalTypeName;
    private Type unmarshalType;
    private boolean prettyPrint;
    private String encoding = "UTF-8";
    private String binaryStrategy = BinaryDataStrategy.BASE_64;
    private String propertyOrder = PropertyOrderStrategy.ANY;
    private String propertyamingStrategy = PropertyNamingStrategy.IDENTITY;
    private boolean skipNull = true;

    public JsonbDataFormat() {
        this(Object.class);
    }

    /**
     * Use the default JSON-B {@link Jsonb} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JsonbDataFormat(Type unmarshalType) {
        this(null, unmarshalType);
    }

    /**
     * Use a custom JSON-B instance and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JsonbDataFormat(Jsonb mapper, Type unmarshalType) {
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
        return "json-jsonb";
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

    public Type getUnmarshalType() {
        return unmarshalType;
    }

    public void setUnmarshalType(Type unmarshalType) {
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

    public String getPropertyamingStrategy() {
        return propertyamingStrategy;
    }

    public void setPropertyamingStrategy(String propertyamingStrategy) {
        this.propertyamingStrategy = propertyamingStrategy;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) {
        objectMapper.toJson(graph, stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        // is there a header with the unmarshal type?
        Type expectedType = unmarshalType;
        String type = exchange.getIn().getHeader("CamelJsonbUnmarshallType", String.class);
        if (type != null) {
            expectedType = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
        }
        return objectMapper.fromJson(stream, expectedType);
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
                    .withPropertyNamingStrategy(propertyamingStrategy)
                    .withEncoding(encoding));
        }
    }

    @Override
    protected void doStop() {
        // noop
    }
}
