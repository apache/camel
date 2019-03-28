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
package org.apache.camel.component.dozer;

import org.apache.camel.converter.dozer.DozerBeanMapperConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static com.github.dozermapper.core.util.DozerConstants.DEFAULT_MAPPING_FILE;

/**
 * Configuration used for a Dozer endpoint.
 */
@UriParams
public class DozerConfiguration {

    @UriPath @Metadata(required = true)
    private String name;
    @UriParam
    private String marshalId;
    @UriParam
    private String unmarshalId;
    @UriParam
    private String sourceModel;
    @UriParam @Metadata(required = true)
    private String targetModel;
    @UriParam(defaultValue = DEFAULT_MAPPING_FILE)
    private String mappingFile;
    @UriParam
    private DozerBeanMapperConfiguration mappingConfiguration;
    
    public DozerConfiguration() {
        setMappingFile(DEFAULT_MAPPING_FILE);
    }
    
    public String getMarshalId() {
        return marshalId;
    }

    /**
     * The id of a dataFormat defined within the Camel Context to use for marshalling the mapping output to a non-Java type.
     */
    public void setMarshalId(String marshalId) {
        this.marshalId = marshalId;
    }

    public String getUnmarshalId() {
        return unmarshalId;
    }

    /**
     * The id of a dataFormat defined within the Camel Context to use for unmarshalling the mapping input from a non-Java type.
     */
    public void setUnmarshalId(String unmarshalId) {
        this.unmarshalId = unmarshalId;
    }

    public String getSourceModel() {
        return sourceModel;
    }

    /**
     * Fully-qualified class name for the source type used in the mapping. If specified, the input to the mapping is converted to the specified type before being mapped with Dozer.
     */
    public void setSourceModel(String sourceModel) {
        this.sourceModel = sourceModel;
    }

    public String getTargetModel() {
        return targetModel;
    }

    /**
     * Fully-qualified class name for the target type used in the mapping.
     */
    public void setTargetModel(String targetModel) {
        this.targetModel = targetModel;
    }

    public String getName() {
        return name;
    }

    /**
     * A human readable name of the mapping.
     */
    public void setName(String name) {
        this.name = name;
    }
    
    public String getMappingFile() {
        return mappingFile;
    }

    /**
     * The location of a Dozer configuration file. The file is loaded from the classpath by default,
     * but you can use file:, classpath:, or http: to load the configuration from a specific location.
     */
    public void setMappingFile(String mappingFile) {
        this.mappingFile = mappingFile;
    }
    
    public DozerBeanMapperConfiguration getMappingConfiguration() {
        return mappingConfiguration;
    }

    /**
     * The name of a DozerBeanMapperConfiguration bean in the Camel registry which should be used for configuring the Dozer mapping.
     * This is an alternative to the mappingFile option that can be used for fine-grained control over how Dozer is configured.
     * Remember to use a "#" prefix in the value to indicate that the bean is in the Camel registry (e.g. "#myDozerConfig").
     */
    public void setMappingConfiguration(DozerBeanMapperConfiguration mappingConfiguration) {
        this.mappingConfiguration = mappingConfiguration;
    }
}
