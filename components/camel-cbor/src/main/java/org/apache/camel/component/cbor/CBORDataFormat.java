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
package org.apache.camel.component.cbor;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.cbor.CBORReadFeature;
import tools.jackson.dataformat.cbor.CBORWriteFeature;

@Dataformat("cbor")
@BodyTypes(marshalIn = @BodyType(value = { Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                 description = "A Map, a List or a POJO to write as CBOR. A String or bytes are written as one"
                                               + " CBOR text or byte string, not as they are"),
           marshalOut = @BodyType(value = Carrier.STREAM, format = "binary",
                                  description = "The CBOR data in the stream cache, or a byte array when stream caching is"
                                                + " off"),
           unmarshalIn = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, format = "binary",
                                   description = "CBOR data. A String fails"),
           unmarshalOut = @BodyType(value = { Carrier.MAP, Carrier.LIST },
                                    dependsOn = { "collectionType", "useList", "allowUnmarshallType", "allowJmsType" },
                                    javaTypeOption = "unmarshalType",
                                    byOption = @BodyType.When(option = "useList", value = "true", carriers = Carrier.LIST),
                                    description = "A Map (LinkedHashMap) for a CBOR map, a List (ArrayList) for an array"))
public class CBORDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(CBORDataFormat.class);

    private CamelContext camelContext;
    private ObjectMapper objectMapper;
    private String unmarshalTypeName;
    private Class<?> unmarshalType;
    private boolean useDefaultObjectMapper = true;
    private boolean allowUnmarshallType;
    private String collectionTypeName;
    private Class<? extends Collection> collectionType;
    private boolean useList;
    private boolean prettyPrint;
    private boolean allowJmsType;
    private String enableFeatures;
    private String disableFeatures;
    private boolean enableJacksonTypeConverter;

    /**
     * Use the default CBOR Jackson {@link ObjectMapper} and {@link Object}
     */
    public CBORDataFormat() {
        // as Jackson does: a Map for a CBOR map, a List for an array
        this.unmarshalType = Object.class;
    }

    /**
     * Use the default CBOR Jackson {@link ObjectMapper} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public CBORDataFormat(ObjectMapper objectMapper, Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
        this.objectMapper = objectMapper;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        stream.write(this.objectMapper.writeValueAsBytes(graph));
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return unmarshal(exchange, (Object) stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, Object body) throws Exception {
        Class<?> clazz = unmarshalType;
        String type = null;
        if (allowUnmarshallType) {
            type = exchange.getIn().getHeader(CBORConstants.UNMARSHAL_TYPE, String.class);
        }
        if (type == null && isAllowJmsType()) {
            type = exchange.getIn().getHeader("JMSType", String.class);
        }
        if (type != null) {
            clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
        }

        ObjectReader reader;
        if (collectionType != null) {
            CollectionType collType = objectMapper.getTypeFactory().constructCollectionType(collectionType, clazz);
            reader = this.objectMapper.readerFor(collType);
        } else {
            reader = this.objectMapper.readerFor(clazz);
        }

        // unwrap file (such as from camel-file)
        if (body instanceof WrappedFile<?>) {
            body = ((WrappedFile<?>) body).getBody();
        }
        Object answer;
        if (body instanceof String b) {
            answer = reader.readValue(b);
        } else if (body instanceof byte[] arr) {
            answer = reader.readValue(arr);
        } else if (body instanceof Reader r) {
            answer = reader.readValue(r);
        } else if (body instanceof File f) {
            answer = reader.readValue(f);
        } else if (body instanceof JsonNode n) {
            answer = reader.readValue(n);
        } else {
            // fallback to input stream
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
            answer = reader.readValue(is);
        }

        return answer;
    }

    @Override
    public String getDataFormatName() {
        return "cbor";
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public boolean isAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    public void setAllowUnmarshallType(boolean allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
    }

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

    public void setCollectionTypeName(String collectionTypeName) {
        this.collectionTypeName = collectionTypeName;
    }

    public Class<? extends Collection> getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(Class<? extends Collection> collectionType) {
        this.collectionType = collectionType;
    }

    public boolean isUseList() {
        return useList;
    }

    public void setUseList(boolean useList) {
        this.useList = useList;
    }

    public boolean isUseDefaultObjectMapper() {
        return useDefaultObjectMapper;
    }

    public void setUseDefaultObjectMapper(boolean useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
    }

    /**
     * Uses {@link java.util.ArrayList} when unmarshalling.
     */
    public void useList() {
        setCollectionType(ArrayList.class);
    }

    /**
     * Uses {@link java.util.HashMap} when unmarshalling.
     */
    public void useMap() {
        setCollectionType(null);
        setUnmarshalType(HashMap.class);
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Allows jackson to use the <tt>JMSType</tt> header as an indicator what the classname is for unmarshaling json
     * content to POJO
     * <p/>
     * By default this option is <tt>false</tt>.
     */
    public void setAllowJmsType(boolean allowJmsType) {
        this.allowJmsType = allowJmsType;
    }

    public boolean isAllowJmsType() {
        return allowJmsType;
    }

    public String getEnableFeatures() {
        return enableFeatures;
    }

    public boolean isEnableJacksonTypeConverter() {
        return enableJacksonTypeConverter;
    }

    /**
     * If enabled then Jackson is allowed to attempt to be used during Camels
     * <a href="https://camel.apache.org/type-converter.html">type converter</a> as a
     * {@link org.apache.camel.FallbackConverter} that attempts to convert POJOs to/from {@link Map}/{@link List} types.
     * <p/>
     * This should only be enabled when desired to be used.
     */
    public void setEnableJacksonTypeConverter(boolean enableJacksonTypeConverter) {
        this.enableJacksonTypeConverter = enableJacksonTypeConverter;
    }

    /**
     * Set of features to enable on the Jackson {@link ObjectMapper}. The features should be a name that matches a enum
     * from {@link SerializationFeature}, {@link DeserializationFeature}, {@link MapperFeature},
     * {@link DateTimeFeature}, {@link EnumFeature}, {@link JsonNodeFeature}, {@link StreamReadFeature} or
     * {@link StreamWriteFeature}.
     */
    public void setEnableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
    }

    public String getDisableFeatures() {
        return disableFeatures;
    }

    /**
     * Set of features to disable on the Jackson {@link ObjectMapper}. The features should be a name that matches a enum
     * from {@link SerializationFeature}, {@link DeserializationFeature}, {@link MapperFeature},
     * {@link DateTimeFeature}, {@link EnumFeature}, {@link JsonNodeFeature}, {@link StreamReadFeature} or
     * {@link StreamWriteFeature}.
     */
    public void setDisableFeatures(String disableFeatures) {
        this.disableFeatures = disableFeatures;
    }

    public void enableFeature(SerializationFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.name();
        } else {
            enableFeatures += "," + feature.name();
        }
    }

    public void enableFeature(DeserializationFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.name();
        } else {
            enableFeatures += "," + feature.name();
        }
    }

    public void enableFeature(MapperFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.name();
        } else {
            enableFeatures += "," + feature.name();
        }
    }

    public void disableFeature(SerializationFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.name();
        } else {
            disableFeatures += "," + feature.name();
        }
    }

    public void disableFeature(DeserializationFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.name();
        } else {
            disableFeatures += "," + feature.name();
        }
    }

    public void disableFeature(MapperFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.name();
        } else {
            disableFeatures += "," + feature.name();
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (unmarshalTypeName != null && (unmarshalType == null || unmarshalType == Object.class)) {
            unmarshalType = camelContext.getClassResolver().resolveClass(unmarshalTypeName);
        }
        if (collectionTypeName != null && collectionType == null) {
            Class<?> clazz = camelContext.getClassResolver().resolveClass(collectionTypeName);
            collectionType = CastUtils.cast(clazz);
        }

        if (objectMapper == null) {
            // lookup if there is a single default mapper we can use
            if (useDefaultObjectMapper && camelContext != null) {
                Set<ObjectMapper> set = camelContext.getRegistry().findByType(ObjectMapper.class);
                set = set.stream().filter(om -> om.tokenStreamFactory() instanceof CBORFactory).collect(Collectors.toSet());
                if (set.size() == 1) {
                    objectMapper = set.iterator().next();
                    LOG.info(
                            "Found a single ObjectMapper with a CBORFactory in the registry, so promoting it as the default ObjectMapper: {}",
                            objectMapper);
                } else {
                    LOG.debug(
                            "Found {} ObjectMapper with a CBORFactory in the registry, so cannot promote any as the default ObjectMapper.",
                            set.size());
                }
            }
            // use a fallback object mapper in last resort
            if (objectMapper == null) {
                // only the CBOR encoding stays as in Jackson 2, so data keeps decoding to the same values
                CBORFactory factory = CBORFactory.builder()
                        .disable(CBORWriteFeature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
                        .disable(CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
                        .disable(CBORReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT)
                        .disable(CBORReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT)
                        .build();
                objectMapper = new CBORMapper(factory);
                LOG.debug("Creating new ObjectMapper to use: {}", objectMapper);
            }
        }

        if (useList) {
            setCollectionType(ArrayList.class);
        }

        if (prettyPrint || enableFeatures != null || disableFeatures != null) {
            // the mapper is immutable, so the options apply to a copy
            MapperBuilder<? extends ObjectMapper, ?> builder = objectMapper.rebuild();
            if (prettyPrint) {
                builder.enable(SerializationFeature.INDENT_OUTPUT);
            }
            configureFeatures(builder, enableFeatures, true);
            configureFeatures(builder, disableFeatures, false);
            objectMapper = builder.build();
        }
    }

    private void configureFeatures(MapperBuilder<?, ?> builder, String features, boolean state) {
        if (features == null) {
            return;
        }
        Iterator<?> it = ObjectHelper.createIterator(features);
        while (it.hasNext()) {
            String feature = it.next().toString();
            if (!configureFeature(builder, feature, state)) {
                throw new IllegalArgumentException(
                        (state ? "Enable" : "Disable") + " feature: " + feature
                                                   + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature]");
            }
        }
    }

    private boolean configureFeature(MapperBuilder<?, ?> builder, String feature, boolean state) {
        // it can be different kind
        TypeConverter converter = getCamelContext().getTypeConverter();
        SerializationFeature sf = converter.tryConvertTo(SerializationFeature.class, feature);
        if (sf != null) {
            builder.configure(sf, state);
            return true;
        }
        DeserializationFeature df = converter.tryConvertTo(DeserializationFeature.class, feature);
        if (df != null) {
            builder.configure(df, state);
            return true;
        }
        MapperFeature mf = converter.tryConvertTo(MapperFeature.class, feature);
        if (mf != null) {
            builder.configure(mf, state);
            return true;
        }
        DateTimeFeature dtf = converter.tryConvertTo(DateTimeFeature.class, feature);
        if (dtf != null) {
            builder.configure(dtf, state);
            return true;
        }
        EnumFeature ef = converter.tryConvertTo(EnumFeature.class, feature);
        if (ef != null) {
            builder.configure(ef, state);
            return true;
        }
        JsonNodeFeature nf = converter.tryConvertTo(JsonNodeFeature.class, feature);
        if (nf != null) {
            builder.configure(nf, state);
            return true;
        }
        StreamReadFeature srf = converter.tryConvertTo(StreamReadFeature.class, feature);
        if (srf != null) {
            builder.configure(srf, state);
            return true;
        }
        StreamWriteFeature swf = converter.tryConvertTo(StreamWriteFeature.class, feature);
        if (swf != null) {
            builder.configure(swf, state);
            return true;
        }
        return false;
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
