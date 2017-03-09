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
package org.apache.camel.impl.verifier;

import java.util.Map;
import java.util.Set;

import org.apache.camel.ComponentVerifier;

public class DefaultResultError implements ComponentVerifier.Error {
    private final String code;
    private final String description;
    private final Set<String> parameters;
    private final Map<String, Object> attributes;

    public DefaultResultError(String code, String description, Set<String> parameters, Map<String, Object> attributes) {
        this.code = code;
        this.description = description;
        this.parameters = parameters;
        this.attributes = attributes;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Set<String> getParameters() {
        return parameters;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "DefaultResultError{"
            + "code='" + code + '\''
            + ", description='" + description + '\''
            + ", parameters=" + parameters
            + ", attributes=" + attributes
            + '}';
    }
}
