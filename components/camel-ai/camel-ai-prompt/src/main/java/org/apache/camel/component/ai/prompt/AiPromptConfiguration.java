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

import java.util.HashMap;
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
    @UriParam(description = "Comma-separated list of tags used to group prompts. MCP servers select which prompts to "
                            + "expose using these tags. When omitted, the prompt goes into a default pool that is not "
                            + "exposed to remote clients.")
    private String tags;

    @Metadata(label = "consumer")
    @UriParam(description = "Human-readable description of what this prompt provides. When omitted, defaults to the "
                            + "prompt name.")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "Optional display title for MCP prompt listings.")
    private String title;

    @Metadata(label = "consumer")
    @UriParam(description = "Prompt arguments. Configure each argument with argument.NAME.description=TEXT and/or "
                            + "argument.NAME.required=true or false. An argument is declared by either option.",
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
                copy.arguments = new HashMap<>(arguments);
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
