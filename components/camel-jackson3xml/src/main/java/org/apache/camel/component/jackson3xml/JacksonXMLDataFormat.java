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
package org.apache.camel.component.jackson3xml;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DatatypeFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.dataformat.xml.XmlFactory;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.XmlReadFeature;
import tools.jackson.dataformat.xml.XmlWriteFeature;
import tools.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat}) using
 * <a href="https://github.com/FasterXML/jackson">Jackson</a> to marshal to and from XML.
 */
@Dataformat("jacksonXml")
public class JacksonXMLDataFormat extends ServiceSupport
        implements DataFormat, DataFormatName, DataFormatContentTypeHeader, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonXMLDataFormat.class);

    private CamelContext camelContext;
    private XmlMapper xmlMapper;
    private String collectionTypeName;
    private Class<? extends Collection> collectionType;
    private List<JacksonModule> modules;
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
    private boolean enableJaxbAnnotationModule;
    private String enableFeatures;
    private String disableFeatures;
    private boolean enableJacksonTypeConverter;
    private boolean allowUnmarshallType;
    private boolean contentTypeHeader = true;
    private TimeZone timezone;
    private int maxStringLength;

    /**
     * Use the default Jackson {@link XmlMapper} and {@link Map}
     */
    public JacksonXMLDataFormat() {
        this(LinkedHashMap.class);
    }

    /**
     * Use the default Jackson {@link XmlMapper} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonXMLDataFormat(Class<?> unmarshalType) {
        this(unmarshalType, null);
    }

    /**
     * Use the default Jackson {@link XmlMapper} and with a custom unmarshal type and JSON view
     *
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     *                      https://github.com/FasterXML/jackson-annotations/blob/master/src/main/java/com/fasterxml/jackson/annotation/JsonView.java
     */
    public JacksonXMLDataFormat(Class<?> unmarshalType, Class<?> jsonView) {
        this(unmarshalType, jsonView, true);
    }

    /**
     * Use the default Jackson {@link XmlMapper} and with a custom unmarshal type and JSON view
     *
     * @param unmarshalType              the custom unmarshal type
     * @param jsonView                   marker class to specify properties to be included during marshalling. See also
     *                                   https://github.com/FasterXML/jackson-annotations/blob/master/src/main/java/com/fasterxml/jackson/annotation/JsonView.java
     * @param enableJaxbAnnotationModule if it is true, will enable the JaxbAnnotationModule.
     */
    public JacksonXMLDataFormat(Class<?> unmarshalType, Class<?> jsonView, boolean enableJaxbAnnotationModule) {
        this.unmarshalType = unmarshalType;
        this.jsonView = jsonView;
        this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
    }

    /**
     * Use a custom Jackson mapper and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonXMLDataFormat(XmlMapper mapper, Class<?> unmarshalType) {
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
    public JacksonXMLDataFormat(XmlMapper mapper, Class<?> unmarshalType, Class<?> jsonView) {
        this.xmlMapper = mapper;
        this.unmarshalType = unmarshalType;
        this.jsonView = jsonView;
    }

    @Override
    public String getDataFormatName() {
        return "jacksonXml";
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
        return unmarshal(exchange, (Object) stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, Object body) throws Exception {
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

        ObjectReader reader;
        if (collectionType != null) {
            CollectionType collType = xmlMapper.getTypeFactory().constructCollectionType(collectionType, clazz);
            reader = this.xmlMapper.readerFor(collType);
        } else {
            reader = this.xmlMapper.reader().forType(clazz);
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

    // Properties
    // -------------------------------------------------------------------------

    public XmlMapper getXmlMapper() {
        return this.xmlMapper;
    }

    public void setXmlMapper(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return this.unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
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

    public String getJsonViewTypeName() {
        return jsonViewTypeName;
    }

    public void setJsonViewTypeName(String jsonViewTypeName) {
        this.jsonViewTypeName = jsonViewTypeName;
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

    public List<JacksonModule> getModules() {
        return modules;
    }

    /**
     * To use custom Jackson {@link JacksonModule}s
     */
    public void setModules(List<JacksonModule> modules) {
        this.modules = modules;
    }

    public String getModuleClassNames() {
        return moduleClassNames;
    }

    /**
     * To use the custom Jackson module
     */
    public void addModule(JacksonModule module) {
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
     * Uses {@link java.util.ArrayList} when unmarshalling.
     */
    public void useList() {
        setCollectionType(ArrayList.class);
    }

    /**
     * Uses {@link java.util.LinkedHashMap} when unmarshalling.
     */
    public void useMap() {
        setCollectionType(null);
        setUnmarshalType(LinkedHashMap.class);
    }

    /**
     * Allows jackson to use the <tt>JMSType</tt> header as an indicator what the classname is for unmarshaling XML
     * content to POJO
     * <p/>
     * By default, this option is <tt>false</tt>.
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
     * If enabled then Jackson will set the Content-Type header to <tt>application/xml</tt> when marshalling.
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

    public int getMaxStringLength() {
        return maxStringLength;
    }

    public void setMaxStringLength(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

    public String getEnableFeatures() {
        return enableFeatures;
    }

    /**
     * Set of features to enable on the Jackson {@link XmlMapper}. The features should be a name that matches an enum
     * from {@link SerializationFeature}, {@link DeserializationFeature}, {@link MapperFeature},
     * {@link DateTimeFeature}, {@link EnumFeature}, {@link JsonNodeFeature}, {@link StreamReadFeature},
     * {@link StreamWriteFeature}, {@link XmlReadFeature} or {@link XmlWriteFeature}.
     */
    public void setEnableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
    }

    public String getDisableFeatures() {
        return disableFeatures;
    }

    /**
     * Set of features to disable on the Jackson {@link XmlMapper}. The features should be a name that matches an enum
     * from {@link SerializationFeature}, {@link DeserializationFeature}, {@link MapperFeature},
     * {@link DateTimeFeature}, {@link EnumFeature}, {@link JsonNodeFeature}, {@link StreamReadFeature},
     * {@link StreamWriteFeature}, {@link XmlReadFeature} or {@link XmlWriteFeature}.
     */
    public void setDisableFeatures(String disableFeatures) {
        this.disableFeatures = disableFeatures;
    }

    public void enableFeature(SerializationFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            enableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void enableFeature(DeserializationFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            enableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void enableFeature(MapperFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            enableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void enableFeature(Enum<? extends DatatypeFeature> feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            enableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void enableFeature(StreamReadFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            enableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void enableFeature(StreamWriteFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            enableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void enableFeature(XmlReadFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            enableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void enableFeature(XmlWriteFeature feature) {
        if (enableFeatures == null) {
            enableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            enableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void disableFeature(SerializationFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            disableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void disableFeature(DeserializationFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            disableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void disableFeature(MapperFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            disableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void disableFeature(Enum<? extends DatatypeFeature> feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            disableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void disableFeature(StreamReadFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            disableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void disableFeature(StreamWriteFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            disableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void disableFeature(XmlReadFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            disableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    public void disableFeature(XmlWriteFeature feature) {
        if (disableFeatures == null) {
            disableFeatures = feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        } else {
            disableFeatures += "," + feature.getDeclaringClass().getSimpleName() + "." + feature.name();
        }
    }

    protected XmlMapper createNewXmlMapper() {
        XmlMapper xm = new XmlMapper();
        int len = getMaxStringLength();
        if (len > 0) {
            LOG.debug("Creating XmlMapper with maxStringLength: {}", len);
            XmlFactory factory = XmlFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(len).build()).build();
            xm = XmlMapper.builder(factory).build();
        }
        return xm;
    }

    @Override
    protected void doInit() throws Exception {
        if (unmarshalTypeName != null && (unmarshalType == null || unmarshalType == LinkedHashMap.class)) {
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
        if (xmlMapper == null) {
            xmlMapper = createNewXmlMapper();
        }

        if (enableJaxbAnnotationModule) {
            // Enables JAXB processing
            JakartaXmlBindAnnotationModule module = new JakartaXmlBindAnnotationModule();
            LOG.info("Registering module: {}", module);
            xmlMapper = xmlMapper.rebuild().addModule(module).build();
        }

        if (useList) {
            setCollectionType(ArrayList.class);
        }
        if (include != null) {
            JsonInclude.Include inc
                    = getCamelContext().getTypeConverter().mandatoryConvertTo(JsonInclude.Include.class, include);
            xmlMapper = xmlMapper.rebuild()
                    .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(inc)).build();
        }
        if (prettyPrint) {
            xmlMapper = xmlMapper.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
        }

        if (enableFeatures != null) {
            doEnableFeatures();
        }
        if (disableFeatures != null) {
            doDisableFeatures();
        }

        if (modules != null) {
            registerModules();
        }
        if (moduleClassNames != null) {
            registerModulesByClassNames();
        }
        if (moduleRefs != null) {
            registerModulesByRefs();
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(timezone)) {
            setTimezone();
        }
    }

    private boolean trySetSerializationFeature(String featureValueName, boolean state) {
        SerializationFeature feature
                = getCamelContext().getTypeConverter().tryConvertTo(SerializationFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetDeserializationFeature(String featureValueName, boolean state) {
        DeserializationFeature feature
                = getCamelContext().getTypeConverter().tryConvertTo(DeserializationFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetMapperFeature(String featureValueName, boolean state) {
        MapperFeature feature = getCamelContext().getTypeConverter().tryConvertTo(MapperFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetDateTimeFeature(String featureValueName, boolean state) {
        DateTimeFeature feature = getCamelContext().getTypeConverter().tryConvertTo(DateTimeFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetEnumFeature(String featureValueName, boolean state) {
        EnumFeature feature = getCamelContext().getTypeConverter().tryConvertTo(EnumFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetJsonNodeFeature(String featureValueName, boolean state) {
        JsonNodeFeature feature = getCamelContext().getTypeConverter().tryConvertTo(JsonNodeFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetStreamReadFeature(String featureValueName, boolean state) {
        StreamReadFeature feature
                = getCamelContext().getTypeConverter().tryConvertTo(StreamReadFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetStreamWriteFeature(String featureValueName, boolean state) {
        StreamWriteFeature feature
                = getCamelContext().getTypeConverter().tryConvertTo(StreamWriteFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetXmlReadFeature(String featureValueName, boolean state) {
        XmlReadFeature feature
                = getCamelContext().getTypeConverter().tryConvertTo(XmlReadFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private boolean trySetXmlWriteFeature(String featureValueName, boolean state) {
        XmlWriteFeature feature
                = getCamelContext().getTypeConverter().tryConvertTo(XmlWriteFeature.class, featureValueName);
        if (feature == null) {
            return false;
        }
        setXmlMapper(xmlMapper.rebuild().configure(feature, state).build());
        return true;
    }

    private void doEnableFeatures() {
        Iterator<?> it = ObjectHelper.createIterator(enableFeatures);
        while (it.hasNext()) {
            String enable = it.next().toString();
            long dotCount = enable.chars().filter(ch -> ch == '.').count();
            if (dotCount > 1) {
                throw new IllegalArgumentException("Enable feature: " + enable + " cannot contain more than one '.'");
            }

            String featureClassName = null;
            String featureValueName = enable;

            if (dotCount == 1) {
                String[] parts = enable.split("\\.", 2);
                featureClassName = parts[0];
                featureValueName = parts[1];
            }

            if (featureClassName != null) {
                if (!switch (featureClassName) {
                    case "SerializationFeature" -> trySetSerializationFeature(featureValueName, true);
                    case "DeserializationFeature" -> trySetDeserializationFeature(featureValueName, true);
                    case "MapperFeature" -> trySetMapperFeature(featureValueName, true);
                    case "DateTimeFeature" -> trySetDateTimeFeature(featureValueName, true);
                    case "EnumFeature" -> trySetEnumFeature(featureValueName, true);
                    case "JsonNodeFeature" -> trySetJsonNodeFeature(featureValueName, true);
                    case "StreamReadFeature" -> trySetStreamReadFeature(featureValueName, true);
                    case "StreamWriteFeature" -> trySetStreamWriteFeature(featureValueName, true);
                    case "XmlReadFeature" -> trySetXmlReadFeature(featureValueName, true);
                    case "XmlWriteFeature" -> trySetXmlWriteFeature(featureValueName, true);
                    default -> false;
                }) {
                    throw new IllegalArgumentException(
                            "Enable feature: " + enable
                                                       + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature,XmlReadFeature,XmlWriteFeature]");
                }
            } else {
                if (!trySetSerializationFeature(featureValueName, true)
                        && !trySetDeserializationFeature(featureValueName, true)
                        && !trySetMapperFeature(featureValueName, true)
                        && !trySetDateTimeFeature(featureValueName, true)
                        && !trySetEnumFeature(featureValueName, true)
                        && !trySetJsonNodeFeature(featureValueName, true)
                        && !trySetStreamReadFeature(featureValueName, true)
                        && !trySetStreamWriteFeature(featureValueName, true)
                        && !trySetXmlReadFeature(featureValueName, true)
                        && !trySetXmlWriteFeature(featureValueName, true)) {
                    throw new IllegalArgumentException(
                            "Enable feature: " + featureValueName
                                                       + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature,XmlReadFeature,XmlWriteFeature]");
                }
            }
        }
    }

    private void doDisableFeatures() {
        Iterator<?> it = ObjectHelper.createIterator(disableFeatures);
        while (it.hasNext()) {
            String disable = it.next().toString();
            long dotCount = disable.chars().filter(ch -> ch == '.').count();
            if (dotCount > 1) {
                throw new IllegalArgumentException("Disable feature: " + disable + " cannot contain more than one '.'");
            }

            String featureClassName = null;
            String featureValueName = disable;

            if (dotCount == 1) {
                String[] parts = disable.split("\\.", 2);
                featureClassName = parts[0];
                featureValueName = parts[1];
            }

            if (featureClassName != null) {
                if (!switch (featureClassName) {
                    case "SerializationFeature" -> trySetSerializationFeature(featureValueName, false);
                    case "DeserializationFeature" -> trySetDeserializationFeature(featureValueName, false);
                    case "MapperFeature" -> trySetMapperFeature(featureValueName, false);
                    case "DateTimeFeature" -> trySetDateTimeFeature(featureValueName, false);
                    case "EnumFeature" -> trySetEnumFeature(featureValueName, false);
                    case "JsonNodeFeature" -> trySetJsonNodeFeature(featureValueName, false);
                    case "StreamReadFeature" -> trySetStreamReadFeature(featureValueName, false);
                    case "StreamWriteFeature" -> trySetStreamWriteFeature(featureValueName, false);
                    case "XmlReadFeature" -> trySetXmlReadFeature(featureValueName, false);
                    case "XmlWriteFeature" -> trySetXmlWriteFeature(featureValueName, false);
                    default -> false;
                }) {
                    throw new IllegalArgumentException(
                            "Disable feature: " + disable
                                                       + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature,XmlReadFeature,XmlWriteFeature]");
                }
            } else {
                if (!trySetSerializationFeature(featureValueName, false)
                        && !trySetDeserializationFeature(featureValueName, false)
                        && !trySetMapperFeature(featureValueName, false)
                        && !trySetDateTimeFeature(featureValueName, false)
                        && !trySetEnumFeature(featureValueName, false)
                        && !trySetJsonNodeFeature(featureValueName, false)
                        && !trySetStreamReadFeature(featureValueName, false)
                        && !trySetStreamWriteFeature(featureValueName, false)
                        && !trySetXmlReadFeature(featureValueName, false)
                        && !trySetXmlWriteFeature(featureValueName, false)) {
                    throw new IllegalArgumentException(
                            "Disable feature: " + featureValueName
                                                       + " cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature,XmlReadFeature,XmlWriteFeature]");
                }
            }
        }
    }

    private void registerModules() {
        for (JacksonModule module : modules) {
            LOG.info("Registering module: {}", module);
            xmlMapper = xmlMapper.rebuild().addModule(module).build();
        }
    }

    private void registerModulesByClassNames() throws ClassNotFoundException {
        Iterable<?> it = ObjectHelper.createIterable(moduleClassNames);
        for (Object o : it) {
            String name = o.toString();
            Class<JacksonModule> clazz = camelContext.getClassResolver().resolveMandatoryClass(name, JacksonModule.class);
            JacksonModule module = camelContext.getInjector().newInstance(clazz);
            LOG.info("Registering module: {} -> {}", name, module);
            xmlMapper = xmlMapper.rebuild().addModule(module).build();
        }
    }

    private void registerModulesByRefs() {
        Iterable<?> it = ObjectHelper.createIterable(moduleRefs);
        for (Object o : it) {
            String name = o.toString();
            if (name.startsWith("#")) {
                name = name.substring(1);
            }
            JacksonModule module = CamelContextHelper.mandatoryLookup(camelContext, name, JacksonModule.class);
            LOG.info("Registering module: {} -> {}", name, module);
            xmlMapper = xmlMapper.rebuild().addModule(module).build();
        }
    }

    private void setTimezone() {
        LOG.debug("Setting timezone to XML Mapper: {}", timezone);
        xmlMapper = xmlMapper.rebuild().defaultTimeZone(timezone).build();
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
