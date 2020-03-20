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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Any23 data format is used for parsing data to RDF.
 */
@Metadata(firstVersion = "3.0.0", label = "dataformat,transformation", title = "Any23")
@XmlRootElement(name = "any23")
@XmlAccessorType(XmlAccessType.FIELD)
public class Any23DataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(defaultValue = "RDF4JMODEL", enums = "NTRIPLES,TURTLE,NQUADS,RDFXML,JSONLD,RDFJSON,RDF4JMODEL",
              javaType = "org.apache.camel.dataformat.any23.Any23OutputFormat")
    private String outputFormat;
    @XmlElement(name = "configuration")
    private List<PropertyDefinition> configuration;
    @XmlTransient
    private Map<String, String> configurations;
    @XmlElement
    private List<String> extractors;
    @XmlAttribute
    private String baseURI;

    public Any23DataFormat() {
        super("any23");
    }

    public Any23DataFormat(String baseuri) {
        this();
        this.baseURI = baseuri;
    }

    public Any23DataFormat(String baseuri, Any23Type outputFormat) {
        this(baseuri);
        this.outputFormat = outputFormat.name();
    }

    public Any23DataFormat(String baseuri, Any23Type outputFormat, Map<String, String> configurations) {
        this(baseuri, outputFormat);
        this.outputFormat = outputFormat.name();
        this.configurations = configurations;
    }

    public Any23DataFormat(String baseuri, Any23Type outputFormat, Map<String, String> configurations, List<String> extractors) {
        this(baseuri, outputFormat, configurations);
        this.outputFormat = outputFormat.name();
        this.configurations = configurations;
        this.extractors = extractors;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    /**
     * What RDF syntax to unmarshal as, can be: NTRIPLES, TURTLE, NQUADS,
     * RDFXML, JSONLD, RDFJSON, RDF4JMODEL. It is by default: RDF4JMODEL.
     */
    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public Map<String, String> getConfigurationAsMap() {
        if (configurations == null && configuration != null) {
            configurations = new HashMap<>();
        }
        if (configuration != null) {
            for (PropertyDefinition def : configuration) {
                configurations.put(def.getKey(), def.getValue());
            }
        }
        return configurations;
    }

    public List<PropertyDefinition> getConfiguration() {
        return configuration;
    }

    /**
     * Configurations for Apache Any23 as key-value pairs in order to customize
     * the extraction process. The list of supported parameters can be found
     * <a href=
     * "https://github.com/apache/any23/blob/master/api/src/main/resources/default-configuration.properties">here</a>.
     * If not provided, a default configuration is used.
     */
    public void setConfiguration(List<PropertyDefinition> configuration) {
        this.configuration = configuration;
    }

    /**
     * Configurations for Apache Any23 as key-value pairs in order to customize
     * the extraction process. The list of supported parameters can be found
     * <a href=
     * "https://github.com/apache/any23/blob/master/api/src/main/resources/default-configuration.properties">here</a>.
     * If not provided, a default configuration is used.
     */
    public void setConfiguration(Map<String, String> configuration) {
        this.configuration = new ArrayList<>();
        configuration.forEach((k, v) -> this.configuration.add(new PropertyDefinition(k, v)));
    }

    public List<String> getExtractors() {
        return extractors;
    }

    /**
     * List of Any23 extractors to be used in the unmarshal operation. A list of
     * the available extractors can be found here
     * <a href="https://any23.apache.org/getting-started.html">here</a>. If not
     * provided, all the available extractors are used.
     */
    public void setExtractors(List<String> extractors) {
        this.extractors = extractors;
    }

    public String getBaseURI() {
        return baseURI;
    }

    /**
     * The URI to use as base for building RDF entities if only relative paths
     * are provided.
     */
    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }

}
