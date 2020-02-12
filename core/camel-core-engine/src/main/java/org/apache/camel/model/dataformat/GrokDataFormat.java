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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * The Grok data format is used for unmarshalling unstructured data to objects
 * using Logstash based Grok patterns.
 */
@Metadata(label = "dataformat,transformation", title = "Grok", firstVersion = "3.0.0")
@XmlRootElement(name = "grok")
@XmlAccessorType(XmlAccessType.FIELD)
public class GrokDataFormat extends DataFormatDefinition {
    @XmlAttribute(required = true)
    @Metadata
    private String pattern;

    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String flattened = Boolean.toString(false);

    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String allowMultipleMatchesPerLine = Boolean.toString(true);

    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String namedOnly = Boolean.toString(false);

    public GrokDataFormat() {
        super("grok");
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * The grok pattern to match lines of input
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getFlattened() {
        return flattened;
    }

    /**
     * Turns on flattened mode. In flattened mode the exception is thrown when
     * there are multiple pattern matches with same key.
     */
    public void setFlattened(String flattened) {
        this.flattened = flattened;
    }

    public String getAllowMultipleMatchesPerLine() {
        return allowMultipleMatchesPerLine;
    }

    /**
     * If false, every line of input is matched for pattern only once. Otherwise
     * the line can be scanned multiple times when non-terminal pattern is used.
     */
    public void setAllowMultipleMatchesPerLine(String allowMultipleMatchesPerLine) {
        this.allowMultipleMatchesPerLine = allowMultipleMatchesPerLine;
    }

    public String getNamedOnly() {
        return namedOnly;
    }

    /**
     * Whether to capture named expressions only or not (i.e. %{IP:ip} but not
     * ${IP})
     */
    public void setNamedOnly(String namedOnly) {
        this.namedOnly = namedOnly;
    }

    @Override
    public String toString() {
        return "GrokDataFormat[" + pattern + ']';
    }
}
