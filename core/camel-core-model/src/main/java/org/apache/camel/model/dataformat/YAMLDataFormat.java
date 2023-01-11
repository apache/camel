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
package org.apache.camel.model.dataformat;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Marshal and unmarshal Java objects to and from YAML.
 */
@Metadata(firstVersion = "2.17.0", label = "dataformat,transformation,yaml", title = "YAML")
@XmlRootElement(name = "yaml")
@XmlAccessorType(XmlAccessType.FIELD)
public class YAMLDataFormat extends DataFormatDefinition {

    @XmlTransient
    private ClassLoader classLoader;
    @XmlTransient
    private Class<?> unmarshalType;

    @XmlAttribute
    @Metadata(defaultValue = "SnakeYAML")
    private YAMLLibrary library;
    @XmlAttribute(name = "unmarshalType")
    private String unmarshalTypeName;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String constructor;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String representer;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String dumperOptions;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String resolver;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String useApplicationContextClassLoader;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String prettyFlow;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String allowAnyType;
    @XmlElement(name = "typeFilter")
    private List<YAMLTypeFilterDefinition> typeFilters;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Integer", defaultValue = "50")
    private String maxAliasesForCollections;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String allowRecursiveKeys;

    public YAMLDataFormat() {
        this(YAMLLibrary.SnakeYAML);
    }

    public YAMLDataFormat(YAMLLibrary library) {
        super(library.getDataFormatName());
        this.library = library;
    }

    public YAMLDataFormat(YAMLLibrary library, Class<?> unmarshalType) {
        super(library.getDataFormatName());
        this.library = library;
        this.unmarshalType = unmarshalType;
    }

    private YAMLDataFormat(Builder builder) {
        super(builder.dataFormatName == null ? YAMLLibrary.SnakeYAML.getDataFormatName() : builder.dataFormatName);
        this.classLoader = builder.classLoader;
        this.unmarshalType = builder.unmarshalType;
        this.library = builder.library;
        this.unmarshalTypeName = builder.unmarshalTypeName;
        this.constructor = builder.constructor;
        this.representer = builder.representer;
        this.dumperOptions = builder.dumperOptions;
        this.resolver = builder.resolver;
        this.useApplicationContextClassLoader = builder.useApplicationContextClassLoader;
        this.prettyFlow = builder.prettyFlow;
        this.allowAnyType = builder.allowAnyType;
        this.typeFilters = builder.typeFilters;
        this.maxAliasesForCollections = builder.maxAliasesForCollections;
        this.allowRecursiveKeys = builder.allowRecursiveKeys;
    }

    @Override
    public String getDataFormatName() {
        // yaml data format is special as the name can be from different bundles
        return this.library != null ? this.library.getDataFormatName() : "snakeYaml";
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
     * Class name of the java type to use when unmarshalling
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

    public String getUseApplicationContextClassLoader() {
        return useApplicationContextClassLoader;
    }

    /**
     * Use ApplicationContextClassLoader as custom ClassLoader
     */
    public void setUseApplicationContextClassLoader(String useApplicationContextClassLoader) {
        this.useApplicationContextClassLoader = useApplicationContextClassLoader;
    }

    public String getPrettyFlow() {
        return prettyFlow;
    }

    /**
     * Force the emitter to produce a pretty YAML document when using the flow style.
     */
    public void setPrettyFlow(String prettyFlow) {
        this.prettyFlow = prettyFlow;
    }

    public String getAllowAnyType() {
        return allowAnyType;
    }

    /**
     * Allow any class to be un-marshaled
     */
    public void setAllowAnyType(String allowAnyType) {
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

    public String getMaxAliasesForCollections() {
        return maxAliasesForCollections;
    }

    /**
     * Set the maximum amount of aliases allowed for collections.
     */
    public void setMaxAliasesForCollections(String maxAliasesForCollections) {
        this.maxAliasesForCollections = maxAliasesForCollections;
    }

    public String getAllowRecursiveKeys() {
        return allowRecursiveKeys;
    }

    /**
     * Set whether recursive keys are allowed.
     */
    public void setAllowRecursiveKeys(String allowRecursiveKeys) {
        this.allowRecursiveKeys = allowRecursiveKeys;
    }

    /**
     * {@code Builder} is a specific builder for {@link YAMLDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<YAMLDataFormat> {

        private String dataFormatName;
        private ClassLoader classLoader;
        private Class<?> unmarshalType;
        private YAMLLibrary library = YAMLLibrary.SnakeYAML;
        private String unmarshalTypeName;
        private String constructor;
        private String representer;
        private String dumperOptions;
        private String resolver;
        private String useApplicationContextClassLoader;
        private String prettyFlow;
        private String allowAnyType;
        private List<YAMLTypeFilterDefinition> typeFilters;
        private String maxAliasesForCollections;
        private String allowRecursiveKeys;

        /**
         * Which yaml library to use.
         * <p/>
         * By default it is SnakeYAML
         */
        public Builder library(YAMLLibrary library) {
            this.library = library;
            this.dataFormatName = "yaml-" + library.name().toLowerCase();
            return this;
        }

        /**
         * Class of the object to be created
         */
        public Builder unmarshalType(Class<?> unmarshalType) {
            this.unmarshalType = unmarshalType;
            return this;
        }

        /**
         * Class name of the java type to use when unmarshalling
         */
        public Builder unmarshalTypeName(String unmarshalTypeName) {
            this.unmarshalTypeName = unmarshalTypeName;
            return this;
        }

        /**
         * Set a custom classloader
         */
        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        /**
         * BaseConstructor to construct incoming documents.
         */
        public Builder constructor(String constructor) {
            this.constructor = constructor;
            return this;
        }

        /**
         * Representer to emit outgoing objects.
         */
        public Builder representer(String representer) {
            this.representer = representer;
            return this;
        }

        /**
         * DumperOptions to configure outgoing objects.
         */
        public Builder dumperOptions(String dumperOptions) {
            this.dumperOptions = dumperOptions;
            return this;
        }

        /**
         * Resolver to detect implicit type
         */
        public Builder resolver(String resolver) {
            this.resolver = resolver;
            return this;
        }

        /**
         * Use ApplicationContextClassLoader as custom ClassLoader
         */
        public Builder useApplicationContextClassLoader(String useApplicationContextClassLoader) {
            this.useApplicationContextClassLoader = useApplicationContextClassLoader;
            return this;
        }

        /**
         * Use ApplicationContextClassLoader as custom ClassLoader
         */
        public Builder useApplicationContextClassLoader(boolean useApplicationContextClassLoader) {
            this.useApplicationContextClassLoader = Boolean.toString(useApplicationContextClassLoader);
            return this;
        }

        /**
         * Force the emitter to produce a pretty YAML document when using the flow style.
         */
        public Builder prettyFlow(String prettyFlow) {
            this.prettyFlow = prettyFlow;
            return this;
        }

        /**
         * Force the emitter to produce a pretty YAML document when using the flow style.
         */
        public Builder prettyFlow(boolean prettyFlow) {
            this.prettyFlow = Boolean.toString(prettyFlow);
            return this;
        }

        /**
         * Allow any class to be un-marshaled
         */
        public Builder allowAnyType(String allowAnyType) {
            this.allowAnyType = allowAnyType;
            return this;
        }

        /**
         * Allow any class to be un-marshaled
         */
        public Builder allowAnyType(boolean allowAnyType) {
            this.allowAnyType = Boolean.toString(allowAnyType);
            return this;
        }

        /**
         * Set the types SnakeYAML is allowed to un-marshall
         */
        public Builder typeFilters(List<YAMLTypeFilterDefinition> typeFilters) {
            this.typeFilters = typeFilters;
            return this;
        }

        /**
         * Set the maximum amount of aliases allowed for collections.
         */
        public Builder maxAliasesForCollections(String maxAliasesForCollections) {
            this.maxAliasesForCollections = maxAliasesForCollections;
            return this;
        }

        /**
         * Set the maximum amount of aliases allowed for collections.
         */
        public Builder maxAliasesForCollections(int maxAliasesForCollections) {
            this.maxAliasesForCollections = Integer.toString(maxAliasesForCollections);
            return this;
        }

        /**
         * Set whether recursive keys are allowed.
         */
        public Builder allowRecursiveKeys(String allowRecursiveKeys) {
            this.allowRecursiveKeys = allowRecursiveKeys;
            return this;
        }

        /**
         * Set whether recursive keys are allowed.
         */
        public Builder allowRecursiveKeys(boolean allowRecursiveKeys) {
            this.allowRecursiveKeys = Boolean.toString(allowRecursiveKeys);
            return this;
        }

        @Override
        public YAMLDataFormat end() {
            return new YAMLDataFormat(this);
        }
    }
}
