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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Transform between JSon and java.util.Map or java.util.List objects.
 */
@Metadata(firstVersion = "4.19.0", label = "dataformat,transformation,json", title = "Groovy JSon")
@XmlRootElement(name = "groovyJson")
@XmlAccessorType(XmlAccessType.FIELD)
public class GroovyJSonDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String prettyPrint;

    public GroovyJSonDataFormat() {
        super("groovyJson");
    }

    protected GroovyJSonDataFormat(GroovyJSonDataFormat source) {
        super(source);
        this.prettyPrint = source.prettyPrint;
    }

    private GroovyJSonDataFormat(Builder builder) {
        this();
        this.prettyPrint = builder.prettyPrint;
    }

    @Override
    public GroovyJSonDataFormat copyDefinition() {
        return new GroovyJSonDataFormat(this);
    }

    public String getPrettyPrint() {
        return prettyPrint;
    }

    /**
     * To pretty printing output nicely formatted.
     * <p/>
     * Is by default true.
     */
    public void setPrettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public GroovyJSonDataFormat prettyPrint(boolean prettyPrint) {
        return prettyPrint(Boolean.toString(prettyPrint));
    }

    public GroovyJSonDataFormat prettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
        return this;
    }

    @Override
    public String toString() {
        return "GroovyJSonDataFormat";
    }

    @XmlTransient
    public static class Builder implements DataFormatBuilder<GroovyJSonDataFormat> {

        private String prettyPrint;

        /**
         * To pretty printing output nicely formatted.
         * <p/>
         * Is by default true.
         */
        public Builder prettyPrint(String prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * To pretty printing output nicely formatted.
         * <p/>
         * Is by default true.
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = Boolean.toString(prettyPrint);
            return this;
        }

        @Override
        public GroovyJSonDataFormat end() {
            return new GroovyJSonDataFormat(this);
        }

    }
}
