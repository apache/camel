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
package org.apache.camel.component.dozer;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.dozer.util.DozerConstants.DEFAULT_MAPPING_FILE;


/**
 * Configuration used for a Dozer endpoint.
 */
@UriParams
public class DozerConfiguration {

    @UriPath
    private String name;
    @UriParam
    private String marshalId;
    @UriParam
    private String unmarshalId;
    @UriParam
    private String sourceModel;
    @UriParam
    private String targetModel;
    @UriParam(defaultValue = DEFAULT_MAPPING_FILE)
    private String mappingFile;
    
    public DozerConfiguration() {
        setMappingFile(DEFAULT_MAPPING_FILE);
    }
    
    public String getMarshalId() {
        return marshalId;
    }

    public void setMarshalId(String marshalId) {
        this.marshalId = marshalId;
    }

    public String getUnmarshalId() {
        return unmarshalId;
    }

    public void setUnmarshalId(String unmarshalId) {
        this.unmarshalId = unmarshalId;
    }

    public String getSourceModel() {
        return sourceModel;
    }

    public void setSourceModel(String sourceModel) {
        this.sourceModel = sourceModel;
    }

    public String getTargetModel() {
        return targetModel;
    }

    public void setTargetModel(String targetModel) {
        this.targetModel = targetModel;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public String getMappingFile() {
        return mappingFile;
    }

    public void setMappingFile(String mappingFile) {
        this.mappingFile = mappingFile;
    }
}
