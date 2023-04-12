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
package org.apache.camel.component.gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.LongSerializationPolicy;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;

/**
 * Marshal POJOs to JSON and back using <a href="http://code.google.com/p/google-gson/">Gson</a>
 */
@Dataformat("gson")
@Metadata(includeProperties = "unmarshalType,unmarshalTypeName,prettyPrint,dateFormatPattern,contentTypeHeader")
public class GsonDataFormat extends ServiceSupport
        implements DataFormat, DataFormatName, DataFormatContentTypeHeader, CamelContextAware {

    private CamelContext camelContext;
    private Gson gson;
    private Class<?> unmarshalType;
    private String unmarshalTypeName;
    private Type unmarshalGenericType;
    private List<ExclusionStrategy> exclusionStrategies;
    private LongSerializationPolicy longSerializationPolicy;
    private FieldNamingPolicy fieldNamingPolicy;
    private FieldNamingStrategy fieldNamingStrategy;
    private boolean serializeNulls;
    private boolean prettyPrint;
    private String dateFormatPattern;
    private boolean contentTypeHeader = true;

    public GsonDataFormat() {
        this(Object.class);
    }

    /**
     * Use the default Gson {@link Gson} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public GsonDataFormat(Class<?> unmarshalType) {
        this(null, unmarshalType);
    }

    /**
     * Use the default Gson {@link Gson} and with a custom unmarshal type and {@link ExclusionStrategy}
     *
     * @param      unmarshalType       the custom unmarshal type
     * @param      exclusionStrategies one or more custom ExclusionStrategy implementations
     * @deprecated                     use the setter instead
     */
    @Deprecated
    public GsonDataFormat(Class<?> unmarshalType, ExclusionStrategy... exclusionStrategies) {
        this(null, unmarshalType);
        setExclusionStrategies(Arrays.asList(exclusionStrategies));
    }

    /**
     * Use a custom Gson mapper and and unmarshal type
     *
     * @param gson          the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public GsonDataFormat(Gson gson, Class<?> unmarshalType) {
        this.gson = gson;
        this.unmarshalType = unmarshalType;
    }

    /**
     * Use the default Gson {@link Gson} and with a custom unmarshal generic type
     *
     * @param unmarshalGenericType the custom unmarshal generic type
     */
    public GsonDataFormat(Type unmarshalGenericType) {
        this(null, unmarshalGenericType);
    }

    /**
     * Use a custom Gson mapper and and unmarshal token type
     *
     * @param gson                 the custom mapper
     * @param unmarshalGenericType the custom unmarshal generic type
     */
    public GsonDataFormat(Gson gson, Type unmarshalGenericType) {
        this.gson = gson;
        this.unmarshalGenericType = unmarshalGenericType;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getDataFormatName() {
        return "gson";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        try (final OutputStreamWriter osw = new OutputStreamWriter(stream, ExchangeHelper.getCharsetName(exchange));
             final BufferedWriter writer = IOHelper.buffered(osw)) {
            gson.toJson(graph, writer);
        }

        if (contentTypeHeader) {
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        }
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream stream) throws Exception {
        try (final InputStreamReader isr = new InputStreamReader(stream, ExchangeHelper.getCharsetName(exchange));
             final BufferedReader reader = IOHelper.buffered(isr)) {

            String type = exchange.getIn().getHeader(GsonConstants.UNMARSHAL_TYPE, String.class);
            if (type != null) {
                Class<?> clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
                return gson.fromJson(reader, clazz);
            } else if (unmarshalGenericType == null) {
                return gson.fromJson(reader, unmarshalType);
            } else {
                return gson.fromJson(reader, unmarshalGenericType);
            }
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
        if (gson == null) {
            GsonBuilder builder = new GsonBuilder();
            if (exclusionStrategies != null && !exclusionStrategies.isEmpty()) {
                ExclusionStrategy[] strategies = exclusionStrategies.toArray(new ExclusionStrategy[0]);
                builder.setExclusionStrategies(strategies);
            }
            if (longSerializationPolicy != null) {
                builder.setLongSerializationPolicy(longSerializationPolicy);
            }
            if (fieldNamingPolicy != null) {
                builder.setFieldNamingPolicy(fieldNamingPolicy);
            }
            if (fieldNamingStrategy != null) {
                builder.setFieldNamingStrategy(fieldNamingStrategy);
            }
            if (serializeNulls) {
                builder.serializeNulls();
            }
            if (prettyPrint) {
                builder.setPrettyPrinting();
            }
            if (dateFormatPattern != null) {
                builder.setDateFormat(dateFormatPattern);
            }
            gson = builder.create();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    // Properties
    // -------------------------------------------------------------------------

    public Class<?> getUnmarshalType() {
        return this.unmarshalType;
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

    public Type getUnmarshalGenericType() {
        return this.unmarshalType;
    }

    public void setUnmarshalGenericType(Type unmarshalGenericType) {
        this.unmarshalGenericType = unmarshalGenericType;
    }

    public List<ExclusionStrategy> getExclusionStrategies() {
        return exclusionStrategies;
    }

    public void setExclusionStrategies(List<ExclusionStrategy> exclusionStrategies) {
        this.exclusionStrategies = exclusionStrategies;
    }

    public LongSerializationPolicy getLongSerializationPolicy() {
        return longSerializationPolicy;
    }

    public void setLongSerializationPolicy(LongSerializationPolicy longSerializationPolicy) {
        this.longSerializationPolicy = longSerializationPolicy;
    }

    public FieldNamingPolicy getFieldNamingPolicy() {
        return fieldNamingPolicy;
    }

    public void setFieldNamingPolicy(FieldNamingPolicy fieldNamingPolicy) {
        this.fieldNamingPolicy = fieldNamingPolicy;
    }

    public FieldNamingStrategy getFieldNamingStrategy() {
        return fieldNamingStrategy;
    }

    public void setFieldNamingStrategy(FieldNamingStrategy fieldNamingStrategy) {
        this.fieldNamingStrategy = fieldNamingStrategy;
    }

    /**
     * @deprecated use {@link #isSerializeNulls()} instead
     */
    @Deprecated
    public Boolean getSerializeNulls() {
        return serializeNulls;
    }

    public boolean isSerializeNulls() {
        return serializeNulls;
    }

    /**
     * @deprecated use {@link #setSerializeNulls(boolean)} instead
     */
    @Deprecated
    public void setSerializeNulls(Boolean serializeNulls) {
        this.serializeNulls = serializeNulls;
    }

    public void setSerializeNulls(boolean serializeNulls) {
        this.serializeNulls = serializeNulls;
    }

    /**
     * @deprecated use {@link #isPrettyPrint()} instead
     */
    @Deprecated
    public Boolean getPrettyPrinting() {
        return prettyPrint;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * @deprecated use {@link #setPrettyPrint(boolean)} instead
     */
    @Deprecated
    public void setPrettyPrinting(Boolean prettyPrinting) {
        this.prettyPrint = prettyPrinting;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getDateFormatPattern() {
        return dateFormatPattern;
    }

    public void setDateFormatPattern(String dateFormatPattern) {
        this.dateFormatPattern = dateFormatPattern;
    }

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * If enabled then Gson will set the Content-Type header to <tt>application/json</tt> when marshalling.
     */
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public Gson getGson() {
        return this.gson;
    }

}
