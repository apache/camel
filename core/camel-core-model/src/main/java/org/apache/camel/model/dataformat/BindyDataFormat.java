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

import java.util.Locale;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Marshal and unmarshal Java beans from and to flat payloads (such as CSV, delimited, fixed length formats, or FIX
 * messages).
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation,csv", title = "Bindy")
@XmlRootElement(name = "bindy")
@XmlAccessorType(XmlAccessType.NONE)
public class BindyDataFormat extends DataFormatDefinition {

    private Class<?> classType;

    @XmlAttribute(required = true)
    @Metadata(required = true, javaType = "org.apache.camel.model.dataformat.BindyType", enums = "Csv,Fixed,KeyValue")
    private String type;
    @XmlAttribute(name = "classType")
    private String classTypeAsString;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false")
    private String allowEmptyStream;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "true")
    private String unwrapSingleInstance;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String locale;

    public BindyDataFormat() {
        super("bindy");
    }

    private BindyDataFormat(Builder builder) {
        this();
        this.classType = builder.classType;
        this.type = builder.type;
        this.classTypeAsString = builder.classTypeAsString;
        this.allowEmptyStream = builder.allowEmptyStream;
        this.unwrapSingleInstance = builder.unwrapSingleInstance;
        this.locale = builder.locale;
    }

    public String getType() {
        return type;
    }

    /**
     * Whether to use Csv, Fixed, or KeyValue.
     */
    public void setType(String type) {
        this.type = type;
    }

    public String getClassTypeAsString() {
        return classTypeAsString;
    }

    /**
     * Name of model class to use.
     */
    public void setClassTypeAsString(String classType) {
        this.classTypeAsString = classType;
    }

    @Override
    public String getDataFormatName() {
        if ("Csv".equals(type)) {
            return "bindyCsv";
        } else if ("Fixed".equals(type)) {
            return "bindyFixed";
        } else {
            return "bindyKvp";
        }
    }

    /**
     * Name of model class to use.
     */
    public void setClassType(Class<?> classType) {
        this.classType = classType;
    }

    public Class<?> getClassType() {
        return classType;
    }

    public String getLocale() {
        return locale;
    }

    /**
     * To configure a default locale to use, such as <tt>us</tt> for united states.
     * <p/>
     * To use the JVM platform default locale then use the name <tt>default</tt>
     */
    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getUnwrapSingleInstance() {
        return unwrapSingleInstance;
    }

    /**
     * When unmarshalling should a single instance be unwrapped and returned instead of wrapped in a
     * <tt>java.util.List</tt>.
     */
    public void setUnwrapSingleInstance(String unwrapSingleInstance) {
        this.unwrapSingleInstance = unwrapSingleInstance;
    }

    public String getAllowEmptyStream() {
        return allowEmptyStream;
    }

    /**
     * Whether to allow empty streams in the unmarshal process. If true, no exception will be thrown when a body without
     * records is provided.
     */
    public void setAllowEmptyStream(String allowEmptyStream) {
        this.allowEmptyStream = allowEmptyStream;
    }

    //
    // Fluent builder api
    //

    public BindyDataFormat csv() {
        return type(BindyType.Csv);
    }

    public BindyDataFormat fixed() {
        return type(BindyType.Fixed);
    }

    public BindyDataFormat keyValue() {
        return type(BindyType.KeyValue);
    }

    public BindyDataFormat type(BindyType type) {
        return type(type.name());
    }

    public BindyDataFormat type(String type) {
        this.type = type;
        return this;
    }

    public BindyDataFormat classType(Class<?> classType) {
        this.classType = classType;
        return this;
    }

    public BindyDataFormat classType(String classType) {
        this.classTypeAsString = classType;
        return this;
    }

    public BindyDataFormat locale(Locale locale) {
        return locale(locale.getCountry().isEmpty()
                ? locale.getLanguage() : locale.getLanguage() + "-" + locale.getCountry());
    }

    public BindyDataFormat locale(String locale) {
        this.locale = locale;
        return this;
    }

    public BindyDataFormat unwrapSingleInstance(boolean unwrapSingleInstance) {
        return unwrapSingleInstance(Boolean.toString(unwrapSingleInstance));
    }

    public BindyDataFormat unwrapSingleInstance(String unwrapSingleInstance) {
        this.unwrapSingleInstance = unwrapSingleInstance;
        return this;
    }

    public BindyDataFormat allowEmptyStream(boolean allowEmptyStream) {
        return allowEmptyStream(Boolean.toString(allowEmptyStream));
    }

    public BindyDataFormat allowEmptyStream(String allowEmptyStream) {
        this.allowEmptyStream = allowEmptyStream;
        return this;
    }

    /**
     * {@code Builder} is a specific builder for {@link BindyDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<BindyDataFormat> {

        private Class<?> classType;
        private String type;
        private String classTypeAsString;
        private String allowEmptyStream;
        private String unwrapSingleInstance;
        private String locale;

        /**
         * Whether to use Csv, Fixed, or KeyValue.
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder type(BindyType type) {
            return type(type.name());
        }

        /**
         * Name of model class to use.
         */
        public Builder classType(String classTypeAsString) {
            this.classTypeAsString = classTypeAsString;
            return this;
        }

        /**
         * Name of model class to use.
         */
        public Builder classType(Class<?> classType) {
            this.classType = classType;
            return this;
        }

        /**
         * To configure a default locale to use, such as <tt>us</tt> for united states.
         * <p/>
         * To use the JVM platform default locale then use the name <tt>default</tt>
         */
        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        /**
         * When unmarshalling should a single instance be unwrapped and returned instead of wrapped in a
         * <tt>java.util.List</tt>.
         */
        public Builder unwrapSingleInstance(String unwrapSingleInstance) {
            this.unwrapSingleInstance = unwrapSingleInstance;
            return this;
        }

        /**
         * When unmarshalling should a single instance be unwrapped and returned instead of wrapped in a
         * <tt>java.util.List</tt>.
         */
        public Builder unwrapSingleInstance(boolean unwrapSingleInstance) {
            this.unwrapSingleInstance = Boolean.toString(unwrapSingleInstance);
            return this;
        }

        /**
         * Whether to allow empty streams in the unmarshal process. If true, no exception will be thrown when a body
         * without records is provided.
         */
        public Builder allowEmptyStream(String allowEmptyStream) {
            this.allowEmptyStream = allowEmptyStream;
            return this;
        }

        /**
         * Whether to allow empty streams in the unmarshal process. If true, no exception will be thrown when a body
         * without records is provided.
         */
        public Builder allowEmptyStream(boolean allowEmptyStream) {
            this.allowEmptyStream = Boolean.toString(allowEmptyStream);
            return this;
        }

        @Override
        public BindyDataFormat end() {
            return new BindyDataFormat(this);
        }
    }
}
