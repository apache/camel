/**
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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.ServiceSupport;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * using <a href="http://jackson.codehaus.org/">Jackson</a> to marshal to and from JSON.
 */
public class JacksonDataFormat extends ServiceSupport implements DataFormat {

    private final ObjectMapper objectMapper;
    private Class<? extends Collection> collectionType;
    private Class<?> unmarshalType;
    private Class<?> jsonView;
    private String include;
    private boolean prettyPrint;
    private boolean allowJmsType;
    private boolean useList;

    /**
     * Use the default Jackson {@link ObjectMapper} and {@link Map}
     */
    public JacksonDataFormat() {
        this(HashMap.class);
    }

    /**
     * Use the default Jackson {@link ObjectMapper} and with a custom
     * unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonDataFormat(Class<?> unmarshalType) {
        this(unmarshalType, null);
    }

    /**
     * Use the default Jackson {@link ObjectMapper} and with a custom
     * unmarshal type and JSON view
     *
     * @param unmarshalType the custom unmarshal type
     * @param jsonView marker class to specify properties to be included during marshalling.
     *                 See also http://wiki.fasterxml.com/JacksonJsonViews
     */
    public JacksonDataFormat(Class<?> unmarshalType, Class<?> jsonView) {
        this.objectMapper = new ObjectMapper();
        this.unmarshalType = unmarshalType;
        this.jsonView = jsonView;

        // Enables JAXB processing
        JaxbAnnotationModule module = new JaxbAnnotationModule();
        this.objectMapper.registerModule(module);
    }

    /**
     * Use a custom Jackson mapper and and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonDataFormat(ObjectMapper mapper, Class<?> unmarshalType) {
        this(mapper, unmarshalType, null);
    }

    /**
     * Use a custom Jackson mapper, unmarshal type and JSON view
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     * @param jsonView marker class to specify properties to be included during marshalling.
     *                 See also http://wiki.fasterxml.com/JacksonJsonViews
     */
    public JacksonDataFormat(ObjectMapper mapper, Class<?> unmarshalType, Class<?> jsonView) {
        this.objectMapper = mapper;
        this.unmarshalType = unmarshalType;
        this.jsonView = jsonView;
    }

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        this.objectMapper.writerWithView(jsonView).writeValue(stream, graph);
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {

        // is there a header with the unmarshal type?
        Class<?> clazz = unmarshalType;
        String type = exchange.getIn().getHeader(JacksonConstants.UNMARSHAL_TYPE, String.class);
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

    // Properties
    // -------------------------------------------------------------------------

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

    public ObjectMapper getObjectMapper() {
        return this.objectMapper;
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
     * Allows jackson to use the <tt>JMSType</tt> header as an indicator what the classname is for unmarshaling json content to POJO
     * <p/>
     * By default this option is <tt>false</tt>.
     */
    public void setAllowJmsType(boolean allowJmsType) {
        this.allowJmsType = allowJmsType;
    }

    @Override
    protected void doStart() throws Exception {
        if (useList) {
            setCollectionType(ArrayList.class);
        }
        if (include != null) {
            JsonInclude.Include inc = JsonInclude.Include.valueOf(include);
            objectMapper.setSerializationInclusion(inc);
        }
        if (prettyPrint) {
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
