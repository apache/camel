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
package org.apache.camel.component.ai.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for the {@link AiPromptComponent}: prompt metadata, arguments and tags.
 *
 * @since 4.24
 */
@Configurer
@UriParams
public class AiPromptConfiguration implements Cloneable {

    @Metadata(label = "consumer")
    @UriParam(description = "A comma-separated list of tags to register the prompt under. The MCP server exposes the"
                            + " prompts whose tags match its tag patterns. Without tags, the prompt goes into a default"
                            + " pool that the MCP server never exposes.")
    private String tags;

    @Metadata(label = "consumer")
    @UriParam(description = "The description of what the prompt provides, which MCP clients see in prompts/list and"
                            + " receive in the prompts/get result. If not set or blank, the prompt name is used.")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "The human-readable title of the prompt, which MCP clients can show in prompts/list instead"
                            + " of the name. If not set, the prompt has no title.")
    private String title;

    @Metadata(label = "consumer")
    @UriParam(description = "The arguments of the prompt, set as argument.NAME.description=TEXT and"
                            + " argument.NAME.required=true or false, where either option declares the argument NAME and an"
                            + " argument is optional by default. The route receives each argument as a String header named"
                            + " NAME; undeclared arguments of a request are ignored, and a request without a required"
                            + " argument is rejected before the route runs. A NAME that starts with camel or"
                            + " org.apache.camel. in any case is not allowed.",
              prefix = "argument.", multiValue = true)
    private Map<String, String> arguments;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments = arguments;
    }

    public AiPromptConfiguration copy() {
        try {
            AiPromptConfiguration copy = (AiPromptConfiguration) super.clone();
            if (arguments != null) {
                copy.arguments = new LinkedHashMap<>(arguments);
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
