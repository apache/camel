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

package org.apache.camel.component.langchain4j.tools.spec;

import java.util.List;

/**
 * langchain4j Simple Tool parameter implementation, this class can be used to provide multiple properties/input
 * parameters to the tool itself, the NamedJsonSchemaProperty can be then found as headers into the consumer route
 */
public class CamelSimpleToolParameter {

    private final String description;
    private final List<NamedJsonSchemaProperty> properties;

    public CamelSimpleToolParameter(String description, List<NamedJsonSchemaProperty> properties) {
        this.description = description;
        this.properties = properties;
    }

    public List<NamedJsonSchemaProperty> getProperties() {
        return properties;
    }

    public String getDescription() {
        return description;
    }
}
