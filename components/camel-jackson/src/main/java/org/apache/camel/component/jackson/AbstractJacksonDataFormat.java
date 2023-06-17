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
package org.apache.camel.component.jackson;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract superclass of Jackson based data formats.
 */
public abstract class AbstractJacksonDataFormat extends ServiceSupport
        implements DataFormat, DataFormatName, DataFormatContentTypeHeader, CamelContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractJacksonDataFormat.class);

    private CamelContext camelContext;
    private ObjectMapper objectMapper;
    private boolean useDefaultObjectMapper = true;
    private String collectionTypeName;
    private Class<? extends Collection> collectionType;
    private List<Module> modules;
    private String moduleClassNames;
    private String moduleRefs;
    private String unmarshalTypeName;
    private Class<?> unmarshalType;
    private String jsonViewTypeName;
    private Class<?> jsonView;
    private String include;
    private boolean prettyPrint;
    private boolean allowJmsType;
    private boolean useList;
    private String enableFeatures;
    private String disableFeatures;
    private boolean enableJacksonTypeConverter;
    private boolean allowUnmarshallType;
    private boolean contentTypeHeader = true;
    private TimeZone timezone;
    private boolean autoDiscoverObjectMapper;
    private SchemaResolver schemaResolver;
    private boolean autoDiscoverSchemaResolver = true;
    private String namingStrategy;

    /**
     * Use the default Jackson {@link ObjectMapper} and {@link Object}
     */
    protected AbstractJacksonDataFormat() {
        this(Object.class);
    }

    /**
     * Use the default Jackson {@link ObjectMapper} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    protected AbstractJacksonDataFormat(Class<?> unmarshalType) {
        this(unmarshalType, null);
    }

    /**
     * Use the default Jackson {@link ObjectMapper} and with a custom unmarshal type and JSON view
     *
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     *                      https://github.com/FasterXML/jackson-annotations/blob/master/src/main/java/com/fasterxml/jackson/annotation/JsonView.java
     */
    protected AbstractJacksonDataFormat(Class<?> unmarshalType, Class<?> jsonView) {
        this.unmarshalType = unmarshalType;
        this.jsonView = jsonView;
    }

    /**
     * Use a custom Jackson mapper and and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    protected AbstractJacksonDataFormat(ObjectMapper mapper, Class<?> unmarshalType) {
        this(mapper, unmarshalType, null);
    }

    /**
     * Use a custom Jackson mapper, unmarshal type and JSON view
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     *                      https://github.com/FasterXML/jackson-annotations/blob/master/src/main/java/com/fasterxml/jackson/annotation/JsonView.java
     */
    protected AbstractJacksonDataFormat(ObjectMapper mapper, Class<?> unmarshalType, Class<?> jsonView) {
        this.objectMapper = mapper;
        this.unmarshalType = unmarshalType;
        this.jsonView = jsonView;
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
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        FormatSchema schema = null;
        if (this.schemaResolver != null) {
            schema = this.schemaResolver.resolve(exchange);
        }
        this.objectMapper.writerWithView(jsonView).with(schema).writeValue(stream, graph);

        if (contentTypeHeader) {
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, getDefaultContentType());
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        FormatSchema schema = null;
        if (this.schemaResolver != null) {
            schema = this.schemaResolver.resolve(exchange);
        }
        // is there a header with the unmarshal type?
        Class<?> clazz = unmarshalType;
        String type = null;
        if (allowUnmarshallType) {
            type = exchange.getIn().getHeader(JacksonConstants.UNMARSHAL_TYPE, String.class);
        }
        if (type == null && isAllowJmsType()) {
            type = exchange.getIn().getHeader("JMSType", String.class);
        }
        if (type != null) {
            clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
        }
        if (collectionType != null) {
            CollectionType collType = objectMapper.getTypeFactory().constructCollectionType(collectionType, clazz);
            return this.objectMapper.readerFor(collType).with(schema).readValue(stream);
        } else {
            return this.objectMapper.reader(schema).readValue(stream, clazz);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public ObjectMapper getObjectMapper() {
        return this.objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isUseDefaultObjectMapper() {
        return useDefaultObjectMapper;
    }

    public void setUseDefaultObjectMapper(boolean useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
    }

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

    public Class<? extends Collection> getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(Class<? extends Collection> collectionType) {
        this.collectionType = collectionType;
    }

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

    public void setCollectionTypeName(String collectionTypeName) {
        this.collectionTypeName = collectionTypeName;
    }

    public Class<?> getJsonView() {
        return jsonView;
    }

    public void setJsonView(Class<?> jsonView) {
        this.jsonView = jsonView;
    }

    public String getJsonViewTypeName() {
        return jsonViewTypeName;
    }

    public void setJsonViewTypeName(String jsonViewTypeName) {
        this.jsonViewTypeName = jsonViewTypeName;
    }

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    public boolean isAllowJmsType() {
        return allowJmsType;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean isUseList() {
        return useList;
    }

    public void setUseList(boolean useList) {
        this.useList = useList;
    }

    public List<Module> getModules() {
        return modules;
    }

    public String getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(String namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    /**
     * To use custom Jackson {@link Module}s
     */
    public void setModules(List<Module> modules) {
        this.modules = modules;
    }

    public String getModuleClassNames() {
        return moduleClassNames;
    }

    /**
     * To use the custom Jackson module
     */
    public void addModule(Module module) {
        if (this.modules == null) {
            this.modules = new ArrayList<>();
        }
        this.modules.add(module);
    }

    /**
     * To use custom Jackson {@link Module}s specified as a String with FQN class names. Multiple classes can be
     * separated by comma.
     */
    public void setModuleClassNames(String moduleClassNames) {
        this.moduleClassNames = moduleClassNames;
    }

    public String getModuleRefs() {
        return moduleRefs;
    }

    /**
     * To use custom Jackson modules referred from the Camel registry. Multiple modules can be separated by comma.
     */
    public void setModuleRefs(String moduleRefs) {
        this.moduleRefs = moduleRefs;
    }

    /**
     * Uses {@link ArrayList} when unmarshalling.
     */
    public void useList() {
        setCollectionType(ArrayList.class);
    }

    /**
     * Uses {@link HashMap} when unmarshalling.
     */
    public void useMap() {
        setCollectionType(null);
        setUnmarshalType(HashMap.class);
    }

    /**
     * Allows jackson to use the <tt>JMSType</tt> header as an indicator what the classname is for unmarshaling content
     * to POJO
     * <p/>
     * By default this option is <tt>false</tt>.
     */
    public void setAllowJmsType(boolean allowJmsType) {
        this.allowJmsType = allowJmsType;
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

    public boolean isAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    /**
     * If enabled then Jackson is allowed to attempt to use the CamelJacksonUnmarshalType header during the
     * unmarshalling.
     * <p/>
     * This should only be enabled when desired to be used.
     */
    public void setAllowUnmarshallType(boolean allowJacksonUnmarshallType) {
        this.allowUnmarshallType = allowJacksonUnmarshallType;
    }

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * If enabled then Jackson will set the Content-Type header to the correct mime type when marshalling.
     */
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public TimeZone getTimezone() {
        return timezone;
    }

    /**
     * If set then Jackson will use the Timezone when marshalling/unmarshalling.
     */
    public void setTimezone(TimeZone timezone) {
        this.timezone = timezone;
    }

    public boolean isAutoDiscoverObjectMapper() {
        return autoDiscoverObjectMapper;
    }

    /**
     * If set to true and useDefaultObjectMapper is true, then Jackson will look for an objectMapper in the registry.
     */
    public void setAutoDiscoverObjectMapper(boolean autoDiscoverObjectMapper) {
        this.autoDiscoverObjectMapper = autoDiscoverObjectMapper;
    }

    public SchemaResolver getSchemaResolver() {
        return schemaResolver;
    }

    /**
     * Optional schema resolver used to lookup schemas for the data in transit.
     */
    public void setSchemaResolver(SchemaResolver schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    public boolean isAutoDiscoverSchemaResolver() {
        return autoDiscoverSchemaResolver;
    }

    /**
     * When not disabled, the SchemaResolver will be looked up into the registry
     */
    public void setAutoDiscoverSchemaResolver(boolean autoDiscoverSchemaResolver) {
        this.autoDiscoverSchemaResolver = autoDiscoverSchemaResolver;
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
        if (unmarshalTypeName != null && (unmarshalType == null || unmarshalType == Object.class)) {
            unmarshalType = camelContext.getClassResolver().resolveClass(unmarshalTypeName);
        }
        if (jsonViewTypeName != null && jsonView == null) {
            jsonView = camelContext.getClassResolver().resolveClass(jsonViewTypeName);
        }
        if (collectionTypeName != null && collectionType == null) {
            Class<?> clazz = camelContext.getClassResolver().resolveClass(collectionTypeName);
            collectionType = CastUtils.cast(clazz);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (useList) {
            setCollectionType(ArrayList.class);
        }

        boolean objectMapperFoundRegistry = false;
        if (objectMapper == null) {
            // lookup if there is a single default mapper we can use
            if (useDefaultObjectMapper && camelContext != null) {
                if (isAutoDiscoverObjectMapper()) {
                    Set<? extends ObjectMapper> set = camelContext.getRegistry().findByType(getObjectMapperClass());
                    if (set.size() == 1) {
                        objectMapper = set.iterator().next();
                        LOG.debug("Found single ObjectMapper in Registry to use: {}", objectMapper);
                        objectMapperFoundRegistry = true;
                    } else if (set.size() > 1) {
                        LOG.debug(
                                "Found {} ObjectMapper in Registry cannot use as default as there are more than one instance.",
                                set.size());
                    }
                } else {
                    LOG.debug("The option autoDiscoverObjectMapper is set to false, Camel won't search in the registry");
                }
            }
            if (objectMapper == null) {
                objectMapper = createNewObjectMapper();
                LOG.debug("Creating new ObjectMapper to use: {}", objectMapper);
            }
        }

        if (!objectMapperFoundRegistry) {
            if (include != null) {
                JsonInclude.Include inc
                        = getCamelContext().getTypeConverter().mandatoryConvertTo(JsonInclude.Include.class, include);
                objectMapper.setSerializationInclusion(inc);
            }
            if (prettyPrint) {
                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            }

            if (enableFeatures != null) {
                Iterator<?> it = ObjectHelper.createIterator(enableFeatures);
                while (it.hasNext()) {
                    String enable = it.next().toString();
                    // it can be different kind
                    SerializationFeature sf
                            = getCamelContext().getTypeConverter().tryConvertTo(SerializationFeature.class, enable);
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

            if (modules != null) {
                for (Module module : modules) {
                    LOG.debug("Registering module: {}", module);
                    objectMapper.registerModules(module);
                }
            }
            if (moduleClassNames != null) {
                Iterable<?> it = ObjectHelper.createIterable(moduleClassNames);
                for (Object o : it) {
                    String name = o.toString();
                    Class<Module> clazz = camelContext.getClassResolver().resolveMandatoryClass(name, Module.class);
                    Module module = camelContext.getInjector().newInstance(clazz);
                    LOG.debug("Registering module: {} -> {}", name, module);
                    objectMapper.registerModule(module);
                }
            }
            if (moduleRefs != null) {
                Iterable<?> it = ObjectHelper.createIterable(moduleRefs);
                for (Object o : it) {
                    String name = o.toString();
                    if (name.startsWith("#")) {
                        name = name.substring(1);
                    }
                    Module module = CamelContextHelper.mandatoryLookup(camelContext, name, Module.class);
                    LOG.debug("Registering module: {} -> {}", name, module);
                    objectMapper.registerModule(module);
                }
            }
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(timezone)) {
                LOG.debug("Setting timezone to Object Mapper: {}", timezone);
                objectMapper.setTimeZone(timezone);
            }

            if (org.apache.camel.util.ObjectHelper.isNotEmpty(namingStrategy)) {
                PropertyNamingStrategy selectedNamingStrategy = determineNamingStrategy(namingStrategy);
                if (org.apache.camel.util.ObjectHelper.isNotEmpty(selectedNamingStrategy)) {
                    objectMapper.setPropertyNamingStrategy(selectedNamingStrategy);
                }
            }
        } else {
            LOG.debug("The objectMapper was already found in the registry, no customizations will be applied");
        }

        if (schemaResolver == null && isAutoDiscoverSchemaResolver()) {
            if (camelContext != null) {
                Set<SchemaResolver> set = camelContext.getRegistry().findByType(SchemaResolver.class);
                if (set.size() == 1) {
                    schemaResolver = set.iterator().next();
                    LOG.debug("Found single SchemaResolver in Registry to use: {}", schemaResolver);
                } else if (set.size() > 1) {
                    LOG.debug(
                            "Found {} SchemaResolver in Registry cannot use as default as there are more than one instance.",
                            set.size());
                }
            }
        } else {
            LOG.debug("The option autoDiscoverSchemaResolver is set to false, Camel won't search in the registry");
        }
    }

    private PropertyNamingStrategy determineNamingStrategy(String namingStrategy) {
        PropertyNamingStrategy strategy = null;
        switch (namingStrategy) {
            case "LOWER_CAMEL_CASE":
                strategy = PropertyNamingStrategies.LOWER_CAMEL_CASE;
                break;
            case "LOWER_DOT_CASE":
                strategy = PropertyNamingStrategies.LOWER_DOT_CASE;
                break;
            case "LOWER_CASE":
                strategy = PropertyNamingStrategies.LOWER_CASE;
                break;
            case "KEBAB_CASE":
                strategy = PropertyNamingStrategies.KEBAB_CASE;
                break;
            case "SNAKE_CASE":
                strategy = PropertyNamingStrategies.SNAKE_CASE;
                break;
            case "UPPER_CAMEL_CASE":
                strategy = PropertyNamingStrategies.UPPER_CAMEL_CASE;
                break;
            default:
                break;
        }
        return strategy;
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public abstract String getDataFormatName();

    protected abstract ObjectMapper createNewObjectMapper();

    protected abstract Class<? extends ObjectMapper> getObjectMapperClass();

    protected abstract String getDefaultContentType();

}
