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
package org.apache.camel.component.jacksonxml;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a>
 * ({@link DataFormat}) using <a href="http://jackson.codehaus.org/">Jackson</a>
 * to marshal to and from XML.
 */
@Dataformat("jacksonxml")
public class JacksonXMLDataFormat extends ServiceSupport implements DataFormat, DataFormatName, DataFormatContentTypeHeader, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonXMLDataFormat.class);

    private CamelContext camelContext;
    private XmlMapper xmlMapper;
    private Class<? extends Collection> collectionType;
    private List<Module> modules;
    private String moduleClassNames;
    private String moduleRefs;
    private Class<?> unmarshalType;
    private Class<?> jsonView;
    private String include;
    private boolean prettyPrint;
    private boolean allowJmsType;
    private boolean useList;
    private boolean enableJaxbAnnotationModule;
    private String enableFeatures;
    private String disableFeatures;
    private boolean enableJacksonTypeConverter;
    private boolean allowUnmarshallType;
    private boolean contentTypeHeader = true;
    private TimeZone timezone;

    /**
     * Use the default Jackson {@link XmlMapper} and {@link Map}
     */
    public JacksonXMLDataFormat() {
        this(HashMap.class);
    }

    /**
     * Use the default Jackson {@link XmlMapper} and with a custom unmarshal
     * type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonXMLDataFormat(Class<?> unmarshalType) {
        this(unmarshalType, null);
    }

    /**
     * Use the default Jackson {@link XmlMapper} and with a custom unmarshal
     * type and JSON view
     *
     * @param unmarshalType the custom unmarshal type
     * @param jsonView marker class to specify properties to be included during
     *            marshalling. See also
     *            http://wiki.fasterxml.com/JacksonJsonViews
     */
    public JacksonXMLDataFormat(Class<?> unmarshalType, Class<?> jsonView) {
        this(unmarshalType, jsonView, true);
    }

    /**
     * Use the default Jackson {@link XmlMapper} and with a custom unmarshal
     * type and JSON view
     *
     * @param unmarshalType the custom unmarshal type
     * @param jsonView marker class to specify properties to be included during
     *            marshalling. See also
     *            http://wiki.fasterxml.com/JacksonJsonViews
     * @param enableJaxbAnnotationModule if it is true, will enable the
     *            JaxbAnnotationModule.
     */
    public JacksonXMLDataFormat(Class<?> unmarshalType, Class<?> jsonView, boolean enableJaxbAnnotationModule) {
        this.unmarshalType = unmarshalType;
        this.jsonView = jsonView;
        this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
    }

    /**
     * Use a custom Jackson mapper and and unmarshal type
     *
     * @param mapper the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonXMLDataFormat(XmlMapper mapper, Class<?> unmarshalType) {
        this(mapper, unmarshalType, null);
    }

    /**
     * Use a custom Jackson mapper, unmarshal type and JSON view
     *
     * @param mapper the custom mapper
     * @param unmarshalType the custom unmarshal type
     * @param jsonView marker class to specify properties to be included during
     *            marshalling. See also
     *            http://wiki.fasterxml.com/JacksonJsonViews
     */
    public JacksonXMLDataFormat(XmlMapper mapper, Class<?> unmarshalType, Class<?> jsonView) {
        this.xmlMapper = mapper;
        this.unmarshalType = unmarshalType;
        this.jsonView = jsonView;
    }

    @Override
    public String getDataFormatName() {
        return "xml-jackson";
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
        this.xmlMapper.writerWithView(jsonView).writeValue(stream, graph);

        if (contentTypeHeader) {
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/xml");
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {

        // is there a header with the unmarshal type?
        Class<?> clazz = unmarshalType;
        String type = null;
        if (allowUnmarshallType) {
            type = exchange.getIn().getHeader(JacksonXMLConstants.UNMARSHAL_TYPE, String.class);
        }
        if (type == null && isAllowJmsType()) {
            type = exchange.getIn().getHeader("JMSType", String.class);
        }
        if (type != null) {
            clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
        }
        if (collectionType != null) {
            CollectionType collType = xmlMapper.getTypeFactory().constructCollectionType(collectionType, clazz);
            return this.xmlMapper.readValue(stream, collType);
        } else {
            return this.xmlMapper.readValue(stream, clazz);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public XmlMapper getXmlMapper() {
        return this.xmlMapper;
    }

    public void setXmlMapper(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public Class<?> getUnmarshalType() {
        return this.unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public Class<? extends Collection> getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(Class<? extends Collection> collectionType) {
        this.collectionType = collectionType;
    }

    public Class<?> getJsonView() {
        return jsonView;
    }

    public void setJsonView(Class<?> jsonView) {
        this.jsonView = jsonView;
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

    public boolean isEnableJaxbAnnotationModule() {
        return enableJaxbAnnotationModule;
    }

    public void setEnableJaxbAnnotationModule(boolean enableJaxbAnnotationModule) {
        this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
    }

    public List<Module> getModules() {
        return modules;
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
     * To use custom Jackson {@link Module}s specified as a String with FQN
     * class names. Multiple classes can be separated by comma.
     */
    public void setModuleClassNames(String moduleClassNames) {
        this.moduleClassNames = moduleClassNames;
    }

    public String getModuleRefs() {
        return moduleRefs;
    }

    /**
     * To use custom Jackson modules referred from the Camel registry. Multiple
     * modules can be separated by comma.
     */
    public void setModuleRefs(String moduleRefs) {
        this.moduleRefs = moduleRefs;
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

    /**
     * Allows jackson to use the <tt>JMSType</tt> header as an indicator what
     * the classname is for unmarshaling XML content to POJO
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
     * <a href="https://camel.apache.org/type-converter.html">type converter</a>
     * as a {@link org.apache.camel.FallbackConverter} that attempts to convert
     * POJOs to/from {@link Map}/{@link List} types.
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
     * If enabled then Jackson is allowed to attempt to use the
     * CamelJacksonUnmarshalType header during the unmarshalling.
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
     * If enabled then Jackson will set the Content-Type header to
     * <tt>application/xml</tt> when marshalling.
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

    public String getEnableFeatures() {
        return enableFeatures;
    }

    /**
     * Set of features to enable on the Jackson {@link XmlMapper}. The features
     * should be a name that matches a enum from {@link SerializationFeature},
     * {@link DeserializationFeature}, or {@link MapperFeature}.
     */
    public void setEnableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
    }

    public String getDisableFeatures() {
        return disableFeatures;
    }

    /**
     * Set of features to disable on the Jackson {@link XmlMapper}. The features
     * should be a name that matches a enum from {@link SerializationFeature},
     * {@link DeserializationFeature}, or {@link MapperFeature}.
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
        if (xmlMapper == null) {
            xmlMapper = new XmlMapper();
        }

        if (enableJaxbAnnotationModule) {
            // Enables JAXB processing
            JaxbAnnotationModule module = new JaxbAnnotationModule();
            LOG.info("Registering module: {}", module);
            xmlMapper.registerModule(module);
        }

        if (useList) {
            setCollectionType(ArrayList.class);
        }
        if (include != null) {
            JsonInclude.Include inc = getCamelContext().getTypeConverter().mandatoryConvertTo(JsonInclude.Include.class, include);
            xmlMapper.setSerializationInclusion(inc);
        }
        if (prettyPrint) {
            xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        if (enableFeatures != null) {
            Iterator<?> it = ObjectHelper.createIterator(enableFeatures);
            while (it.hasNext()) {
                String enable = it.next().toString();
                // it can be different kind
                SerializationFeature sf = getCamelContext().getTypeConverter().tryConvertTo(SerializationFeature.class, enable);
                if (sf != null) {
                    xmlMapper.enable(sf);
                    continue;
                }
                DeserializationFeature df = getCamelContext().getTypeConverter().tryConvertTo(DeserializationFeature.class, enable);
                if (df != null) {
                    xmlMapper.enable(df);
                    continue;
                }
                MapperFeature mf = getCamelContext().getTypeConverter().tryConvertTo(MapperFeature.class, enable);
                if (mf != null) {
                    xmlMapper.enable(mf);
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
                    xmlMapper.disable(sf);
                    continue;
                }
                DeserializationFeature df = getCamelContext().getTypeConverter().tryConvertTo(DeserializationFeature.class, disable);
                if (df != null) {
                    xmlMapper.disable(df);
                    continue;
                }
                MapperFeature mf = getCamelContext().getTypeConverter().tryConvertTo(MapperFeature.class, disable);
                if (mf != null) {
                    xmlMapper.disable(mf);
                    continue;
                }
                throw new IllegalArgumentException("Disable feature: " + disable
                                                   + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature]");
            }
        }

        if (modules != null) {
            for (Module module : modules) {
                LOG.info("Registering module: {}", module);
                xmlMapper.registerModules(module);
            }
        }
        if (moduleClassNames != null) {
            Iterable<?> it = ObjectHelper.createIterable(moduleClassNames);
            for (Object o : it) {
                String name = o.toString();
                Class<Module> clazz = camelContext.getClassResolver().resolveMandatoryClass(name, Module.class);
                Module module = camelContext.getInjector().newInstance(clazz);
                LOG.info("Registering module: {} -> {}", name, module);
                xmlMapper.registerModule(module);
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
                LOG.info("Registering module: {} -> {}", name, module);
                xmlMapper.registerModule(module);
            }
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(timezone)) {
            LOG.debug("Setting timezone to XML Mapper: {}", timezone);
            xmlMapper.setTimeZone(timezone);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
