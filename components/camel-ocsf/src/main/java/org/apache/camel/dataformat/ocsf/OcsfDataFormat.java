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
package org.apache.camel.dataformat.ocsf;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.dataformat.ocsf.model.OcsfEvent;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OCSF (Open Cybersecurity Schema Framework) DataFormat.
 * <p>
 * Marshals POJOs to OCSF-compliant JSON and unmarshals OCSF JSON to POJOs. This data format is designed to work with
 * security events following the OCSF schema specification.
 *
 * @see <a href="https://schema.ocsf.io/">OCSF Schema</a>
 */
@Dataformat("ocsf")
@Metadata(excludeProperties = "library")
public class OcsfDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(OcsfDataFormat.class);

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
    private String enableFeatures;
    private String disableFeatures;

    /**
     * Use the default Jackson {@link ObjectMapper} and {@link OcsfEvent} as unmarshal type.
     */
    public OcsfDataFormat() {
        this.unmarshalType = OcsfEvent.class;
    }

    /**
     * Use the default Jackson {@link ObjectMapper} and with a custom unmarshal type.
     *
     * @param unmarshalType the custom unmarshal type
     */
    public OcsfDataFormat(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    /**
     * Use a custom Jackson mapper and an unmarshal type.
     *
     * @param objectMapper  the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public OcsfDataFormat(ObjectMapper objectMapper, Class<?> unmarshalType) {
        this.objectMapper = objectMapper;
        this.unmarshalType = unmarshalType;
    }

    @Override
    public String getDataFormatName() {
        return "ocsf";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        objectMapper.writeValue(stream, graph);
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
            type = exchange.getIn().getHeader(OcsfConstants.UNMARSHAL_TYPE, String.class);
        }
        if (type != null) {
            clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
        }

        ObjectReader reader;
        if (collectionType != null) {
            CollectionType collType = objectMapper.getTypeFactory().constructCollectionType(collectionType, clazz);
            reader = objectMapper.readerFor(collType);
        } else {
            reader = objectMapper.readerFor(clazz);
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
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Sets a custom Jackson {@link ObjectMapper} to use.
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    /**
     * Class name of the OCSF event type to unmarshal to.
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class of the OCSF event type to unmarshal to.
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public boolean isAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    /**
     * If enabled, allows the unmarshal type to be specified via the {@link OcsfConstants#UNMARSHAL_TYPE} header.
     */
    public void setAllowUnmarshallType(boolean allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
    }

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

    /**
     * Sets the collection type name to use when unmarshalling to a collection.
     */
    public void setCollectionTypeName(String collectionTypeName) {
        this.collectionTypeName = collectionTypeName;
    }

    public Class<? extends Collection> getCollectionType() {
        return collectionType;
    }

    /**
     * Sets the collection type to use when unmarshalling to a collection.
     */
    public void setCollectionType(Class<? extends Collection> collectionType) {
        this.collectionType = collectionType;
    }

    public boolean isUseList() {
        return useList;
    }

    /**
     * To unmarshal to a List of OCSF events.
     */
    public void setUseList(boolean useList) {
        this.useList = useList;
    }

    public boolean isUseDefaultObjectMapper() {
        return useDefaultObjectMapper;
    }

    /**
     * Whether to look up and use a default Jackson ObjectMapper from the registry.
     */
    public void setUseDefaultObjectMapper(boolean useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
    }

    /**
     * Uses {@link java.util.ArrayList} when unmarshalling.
     */
    public void useList() {
        setCollectionType(ArrayList.class);
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * To enable pretty printing output nicely formatted.
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getEnableFeatures() {
        return enableFeatures;
    }

    /**
     * Set of features to enable on the Jackson {@link ObjectMapper}. The features should be a name that matches a enum
     * from {@link SerializationFeature}, {@link DeserializationFeature}, or {@link MapperFeature}.
     */
    public void setEnableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
    }

    public String getDisableFeatures() {
        return disableFeatures;
    }

    /**
     * Set of features to disable on the Jackson {@link ObjectMapper}. The features should be a name that matches a enum
     * from {@link SerializationFeature}, {@link DeserializationFeature}, or {@link MapperFeature}.
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

        if (unmarshalTypeName != null && unmarshalType == null) {
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
                if (set.size() == 1) {
                    objectMapper = set.iterator().next();
                    LOG.info("Found a single ObjectMapper in the registry, so promoting it as the default ObjectMapper: {}",
                            objectMapper);
                } else {
                    LOG.debug("Found {} ObjectMapper in the registry, so cannot promote any as the default ObjectMapper.",
                            set.size());
                }
            }
            // use a fallback object mapper in last resort
            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
                // Configure for OCSF compatibility
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                // Register JavaTimeModule for java.time.Instant support
                objectMapper.registerModule(new JavaTimeModule());
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
                DeserializationFeature df
                        = getCamelContext().getTypeConverter().tryConvertTo(DeserializationFeature.class, enable);
                if (df != null) {
                    objectMapper.enable(df);
                    continue;
                }
                MapperFeature mf = getCamelContext().getTypeConverter().tryConvertTo(MapperFeature.class, enable);
                if (mf != null) {
                    objectMapper.enable(mf);
                    continue;
                }
                throw new IllegalArgumentException(
                        "Enable feature: " + enable
                                                   + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature]");
            }
        }
        if (disableFeatures != null) {
            Iterator<?> it = ObjectHelper.createIterator(disableFeatures);
            while (it.hasNext()) {
                String disable = it.next().toString();
                // it can be different kind
                SerializationFeature sf
                        = getCamelContext().getTypeConverter().tryConvertTo(SerializationFeature.class, disable);
                if (sf != null) {
                    objectMapper.disable(sf);
                    continue;
                }
                DeserializationFeature df
                        = getCamelContext().getTypeConverter().tryConvertTo(DeserializationFeature.class, disable);
                if (df != null) {
                    objectMapper.disable(df);
                    continue;
                }
                MapperFeature mf = getCamelContext().getTypeConverter().tryConvertTo(MapperFeature.class, disable);
                if (mf != null) {
                    objectMapper.disable(mf);
                    continue;
                }
                throw new IllegalArgumentException(
                        "Disable feature: " + disable
                                                   + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature]");
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
