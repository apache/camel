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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dataformat("cbor")
public class CBORDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private static final Logger LOG = LoggerFactory.getLogger(CBORDataFormat.class);

    private CamelContext camelContext;
    private ObjectMapper objectMapper;
    private Class<?> unmarshalType;
    private boolean useDefaultObjectMapper = true;
    private boolean allowUnmarshallType;
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
    }

    /**
     * Use the default CBOR Jackson {@link ObjectMapper} and with a custom
     * unmarshal type
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
        if (collectionType != null) {
            CollectionType collType = objectMapper.getTypeFactory().constructCollectionType(collectionType, clazz);
            return this.objectMapper.readValue(stream, collType);
        } else {
            return this.objectMapper.readValue(stream, clazz);
        }
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
     * Allows jackson to use the <tt>JMSType</tt> header as an indicator what
     * the classname is for unmarshaling json content to POJO
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
     * <a href="https://camel.apache.org/type-converter.html">type converter</a>
     * as a {@link org.apache.camel.FallbackConverter} that attempts to convert
     * POJOs to/from {@link Map}/{@link List} types.
     * <p/>
     * This should only be enabled when desired to be used.
     */
    public void setEnableJacksonTypeConverter(boolean enableJacksonTypeConverter) {
        this.enableJacksonTypeConverter = enableJacksonTypeConverter;
    }

    /**
     * Set of features to enable on the Jackson {@link ObjectMapper}. The
     * features should be a name that matches a enum from
     * {@link SerializationFeature}, {@link DeserializationFeature}, or
     * {@link MapperFeature}.
     */
    public void setEnableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
    }

    public String getDisableFeatures() {
        return disableFeatures;
    }

    /**
     * Set of features to disable on the Jackson {@link ObjectMapper}. The
     * features should be a name that matches a enum from
     * {@link SerializationFeature}, {@link DeserializationFeature}, or
     * {@link MapperFeature}.
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
    protected void doStart() throws Exception {
        if (objectMapper == null) {
            // lookup if there is a single default mapper we can use
            if (useDefaultObjectMapper && camelContext != null) {
                Set<ObjectMapper> set = camelContext.getRegistry().findByType(ObjectMapper.class);
                if (set.size() == 1) {
                    objectMapper = set.iterator().next();
                    LOG.info("Found single ObjectMapper in Registry to use: {}", objectMapper);
                } else if (set.size() > 1) {
                    LOG.debug("Found {} ObjectMapper in Registry cannot use as default as there are more than one instance.", set.size());
                }
            }
            if (objectMapper == null) {
                CBORFactory factory = new CBORFactory();
                objectMapper = new ObjectMapper(factory);
                LOG.debug("Creating new ObjectMapper to use: {}", objectMapper);
            }
        }

        if (useList) {
            setCollectionType(ArrayList.class);
        }
        
        if (prettyPrint) {
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        
        if (enableFeatures != null) {
            Iterator<?> it = ObjectHelper.createIterator(enableFeatures);
            while (it.hasNext()) {
                String enable = it.next().toString();
                // it can be different kind
                SerializationFeature sf = getCamelContext().getTypeConverter().tryConvertTo(SerializationFeature.class, enable);
                if (sf != null) {
                    objectMapper.enable(sf);
                    continue;
                }
                DeserializationFeature df = getCamelContext().getTypeConverter().tryConvertTo(DeserializationFeature.class, enable);
                if (df != null) {
                    objectMapper.enable(df);
                    continue;
                }
                MapperFeature mf = getCamelContext().getTypeConverter().tryConvertTo(MapperFeature.class, enable);
                if (mf != null) {
                    objectMapper.enable(mf);
                    continue;
                }
                throw new IllegalArgumentException("Enable feature: " + enable
                                                   + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature]");
            }
        }
        if (disableFeatures != null) {
            Iterator<?> it = ObjectHelper.createIterator(disableFeatures);
            while (it.hasNext()) {
                String disable = it.next().toString();
                // it can be different kind
                SerializationFeature sf = getCamelContext().getTypeConverter().tryConvertTo(SerializationFeature.class, disable);
                if (sf != null) {
                    objectMapper.disable(sf);
                    continue;
                }
                DeserializationFeature df = getCamelContext().getTypeConverter().tryConvertTo(DeserializationFeature.class, disable);
                if (df != null) {
                    objectMapper.disable(df);
                    continue;
                }
                MapperFeature mf = getCamelContext().getTypeConverter().tryConvertTo(MapperFeature.class, disable);
                if (mf != null) {
                    objectMapper.disable(mf);
                    continue;
                }
                throw new IllegalArgumentException("Disable feature: " + disable
                                                   + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature]");
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
