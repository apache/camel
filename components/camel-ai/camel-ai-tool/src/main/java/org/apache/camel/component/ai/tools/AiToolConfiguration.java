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
package org.apache.camel.component.ai.tools;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for the {@link AiToolComponent}: tool description, tags, and parameter metadata.
 *
 * @since 4.22
 */
@Configurer
@UriParams
public class AiToolConfiguration implements Cloneable {

    @UriParam(description = "Comma-separated list of tags used to group tools. "
                            + "Producers filter the registry by these tags to select which tools to expose to the LLM. "
                            + "When omitted, the tool goes into a default pool available to all producers.")
    private String tags;

    @Metadata(label = "consumer")
    @UriParam(description = "Human-readable description of what this tool does. "
                            + "Passed verbatim to the LLM; be precise and action-oriented. "
                            + "When omitted, defaults to the tool name.")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool input parameters. "
                            + "Format: parameter.NAME=TYPE, parameter.NAME.description=TEXT, "
                            + "parameter.NAME.required=true or false, parameter.NAME.enum=val1,val2. "
                            + "Supported types: string, integer, number, boolean.",
              prefix = "parameter.", multiValue = true)
    private Map<String, String> parameters;

    public AiToolConfiguration() {
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public AiToolConfiguration copy() {
        try {
            AiToolConfiguration copy = (AiToolConfiguration) super.clone();
            if (this.parameters != null) {
                copy.parameters = new HashMap<>(this.parameters);
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
