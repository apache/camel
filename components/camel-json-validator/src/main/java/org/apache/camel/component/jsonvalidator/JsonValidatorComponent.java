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
package org.apache.camel.component.jsonvalidator;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;

/**
 * The JSON Schema Validator Component is for validating JSON against a schema.
 */
@Component("json-validator")
public class JsonValidatorComponent extends DefaultComponent {

    @Metadata(defaultValue = "true")
    private boolean useDefaultObjectMapper = true;
    @Metadata(label = "advanced")
    private String objectMapper;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        JsonValidatorEndpoint endpoint = new JsonValidatorEndpoint(uri, this, remaining);
        if (objectMapper != null) {
            ObjectMapper om = CamelContextHelper.lookup(getCamelContext(), objectMapper, ObjectMapper.class);
            endpoint.setObjectMapper(om);
        } else if (useDefaultObjectMapper) {
            ObjectMapper om = CamelContextHelper.findSingleByType(getCamelContext(), ObjectMapper.class);
            endpoint.setObjectMapper(om);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public boolean isUseDefaultObjectMapper() {
        return useDefaultObjectMapper;
    }

    /**
     * Whether to lookup and use default Jackson ObjectMapper from the registry.
     */
    public void setUseDefaultObjectMapper(boolean useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
    }

    public String getObjectMapper() {
        return objectMapper;
    }

    /**
     * Lookup and use the existing ObjectMapper with the given id.
     */
    public void setObjectMapper(String objectMapper) {
        this.objectMapper = objectMapper;
    }
}
