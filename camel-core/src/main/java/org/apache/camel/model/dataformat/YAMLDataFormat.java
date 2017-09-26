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
package org.apache.camel.model.dataformat;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * YAML is a data format to marshal and unmarshal Java objects to and from YAML.
 *
 * @version 
 */
@Metadata(firstVersion = "2.17.0", label = "dataformat,transformation,yaml", title = "YAML")
@XmlRootElement(name = "yaml")
@XmlAccessorType(XmlAccessType.FIELD)
public class YAMLDataFormat extends DataFormatDefinition {
    @XmlAttribute @Metadata(defaultValue = "SnakeYAML")
    private YAMLLibrary library = YAMLLibrary.SnakeYAML;
    @XmlTransient
    private ClassLoader classLoader;
    @XmlTransient
    private Class<?> unmarshalType;
    @XmlAttribute
    private String unmarshalTypeName;
    @XmlAttribute
    private String constructor;
    @XmlAttribute
    private String representer;
    @XmlAttribute
    private String dumperOptions;
    @XmlAttribute
    private String resolver;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean useApplicationContextClassLoader = true;
    @XmlAttribute @Metadata(defaultValue = "false")
    private Boolean prettyFlow = false;
    @XmlAttribute @Metadata(defaultValue = "false")
    private Boolean allowAnyType = false;
    @XmlElement(name = "typeFilter")
    private List<YAMLTypeFilterDefinition> typeFilters;

    public YAMLDataFormat() {
        this(YAMLLibrary.SnakeYAML);
    }

    public YAMLDataFormat(YAMLLibrary library) {
        super("yaml-" + library.name().toLowerCase());
        this.library = library;
    }

    public YAMLDataFormat(YAMLLibrary library, Class<?> unmarshalType) {
        super("yaml-" + library.name().toLowerCase());
        this.library = library;
        this.unmarshalType = unmarshalType;
    }

    public YAMLLibrary getLibrary() {
        return library;
    }

    /**
     * Which yaml library to use.
     * <p/>
     * By default it is SnakeYAML
     */
    public void setLibrary(YAMLLibrary library) {
        this.library = library;
        setDataFormatName("yaml-" + library.name().toLowerCase());
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class of the object to be created
     */
    public void setUnmarshalType(Class<?> type) {
        this.unmarshalType = type;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    /**
     * Class name of the java type to use when unarmshalling
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Set a custom classloader
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public String getConstructor() {
        return constructor;
    }

    /**
     * BaseConstructor to construct incoming documents.
     */
    public void setConstructor(String constructor) {
        this.constructor = constructor;
    }

    public String getRepresenter() {
        return representer;
    }

    /**
     * Representer to emit outgoing objects.
     */
    public void setRepresenter(String representer) {
        this.representer = representer;
    }

    public String getDumperOptions() {
        return dumperOptions;
    }

    /**
     * DumperOptions to configure outgoing objects.
     */
    public void setDumperOptions(String dumperOptions) {
        this.dumperOptions = dumperOptions;
    }

    public String getResolver() {
        return resolver;
    }

    /**
     * Resolver to detect implicit type
     */
    public void setResolver(String resolver) {
        this.resolver = resolver;
    }

    public boolean isUseApplicationContextClassLoader() {
        return useApplicationContextClassLoader;
    }

    /**
     * Use ApplicationContextClassLoader as custom ClassLoader
     */
    public void setUseApplicationContextClassLoader(boolean useApplicationContextClassLoader) {
        this.useApplicationContextClassLoader = useApplicationContextClassLoader;
    }

    public boolean isPrettyFlow() {
        return prettyFlow;
    }

    /**
     * Force the emitter to produce a pretty YAML document when using the flow
     * style.
     */
    public void setPrettyFlow(boolean prettyFlow) {
        this.prettyFlow = prettyFlow;
    }

    public boolean isAllowAnyType() {
        return allowAnyType;
    }

    /**
     * Allow any class to be un-marshaled
     */
    public void setAllowAnyType(boolean allowAnyType) {
        this.allowAnyType = allowAnyType;
    }

    public List<YAMLTypeFilterDefinition> getTypeFilters() {
        return typeFilters;
    }

    /**
     * Set the types SnakeYAML is allowed to un-marshall
     */
    public void setTypeFilters(List<YAMLTypeFilterDefinition> typeFilters) {
        this.typeFilters = typeFilters;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (library == YAMLLibrary.SnakeYAML) {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "yaml-snakeyaml");
        }

        return super.createDataFormat(routeContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (library == YAMLLibrary.SnakeYAML) {
            configureSnakeDataFormat(dataFormat, camelContext);
        }
    }

    protected void configureSnakeDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        Class<?> yamlUnmarshalType =  unmarshalType;
        if (yamlUnmarshalType == null && unmarshalTypeName != null) {
            try {
                yamlUnmarshalType = camelContext.getClassResolver().resolveMandatoryClass(unmarshalTypeName);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        setProperty(dataFormat, camelContext, "unmarshalType", yamlUnmarshalType);
        setProperty(dataFormat, camelContext, "classLoader", classLoader);
        setProperty(dataFormat, camelContext, "useApplicationContextClassLoader", useApplicationContextClassLoader);
        setProperty(dataFormat, camelContext, "prettyFlow", prettyFlow);
        setProperty(dataFormat, camelContext, "allowAnyType", allowAnyType);

        if (typeFilters != null && !typeFilters.isEmpty()) {
            List<String> typeFilterDefinitions = new ArrayList<>(typeFilters.size());
            for (YAMLTypeFilterDefinition definition : typeFilters) {
                String value = definition.getValue();

                if (!value.startsWith("type") && !value.startsWith("regexp")) {
                    YAMLTypeFilterType type = definition.getType();
                    if (type == null) {
                        type = YAMLTypeFilterType.type;
                    }

                    value = type.name() + ":" + value;
                }

                typeFilterDefinitions.add(value);
            }

            setProperty(dataFormat, camelContext, "typeFilterDefinitions", typeFilterDefinitions);
        }

        setPropertyRef(dataFormat, camelContext, "constructor", constructor);
        setPropertyRef(dataFormat, camelContext, "representer", representer);
        setPropertyRef(dataFormat, camelContext, "dumperOptions", dumperOptions);
        setPropertyRef(dataFormat, camelContext, "resolver", resolver);
    }

    protected void setProperty(DataFormat dataFormat, CamelContext camelContext, String propertyName, Object propertyValue) {
        if (ObjectHelper.isNotEmpty(propertyValue)) {
            setProperty(camelContext, dataFormat, propertyName, propertyValue);
        }
    }

    protected void setPropertyRef(DataFormat dataFormat, CamelContext camelContext, String propertyName, String propertyValue) {
        if (ObjectHelper.isNotEmpty(propertyValue)) {
            // must be a reference value
            String ref = propertyValue.startsWith("#") ? propertyValue : "#" + propertyValue;
            setProperty(camelContext, dataFormat, propertyName, ref);
        }
    }

}
