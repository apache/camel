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

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import tools.jackson.databind.ObjectMapper;

/**
 * The JSON Schema Validator Component is for validating JSON against a schema.
 */
@Component("json-validator")
public class JsonValidatorComponent extends DefaultComponent {

    @Metadata(defaultValue = "true",
              description = "The contentCache value that the component sets on each endpoint that it creates. The endpoint"
                            + " loads the schema on the first message and keeps it, whatever this value is. The JMX"
                            + " operation clearContentCache makes the endpoint load the schema again on the next message.")
    private boolean contentCache = true;
    @Metadata(defaultValue = "true",
              description = "Whether the endpoints use the Jackson 3 (tools.jackson) ObjectMapper of the registry to parse the"
                            + " JSON to validate, when the registry holds exactly one. The objectMapper option takes"
                            + " precedence. If no mapper is found, the endpoint creates one with the Jackson 2 defaults.")
    private boolean useDefaultObjectMapper = true;
    @Metadata(label = "advanced",
              description = "The id of a Jackson 3 (tools.jackson) ObjectMapper in the registry that the endpoints use to parse"
                            + " the JSON to validate. Takes precedence over useDefaultObjectMapper. Creating an endpoint fails"
                            + " when the registry has no ObjectMapper with this id.")
    private String objectMapper;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        JsonValidatorEndpoint endpoint = new JsonValidatorEndpoint(uri, this, remaining);
        if (objectMapper != null) {
            // mandatory, so a wrong id or a mapper of another Jackson line fails instead of being ignored
            ObjectMapper om = CamelContextHelper.mandatoryLookup(getCamelContext(), objectMapper, ObjectMapper.class);
            endpoint.setObjectMapper(om);
        } else if (useDefaultObjectMapper) {
            ObjectMapper om = CamelContextHelper.findSingleByType(getCamelContext(), ObjectMapper.class);
            endpoint.setObjectMapper(om);
        }
        endpoint.setContentCache(contentCache);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public boolean isContentCache() {
        return contentCache;
    }

    public void setContentCache(boolean contentCache) {
        this.contentCache = contentCache;
    }

    public boolean isUseDefaultObjectMapper() {
        return useDefaultObjectMapper;
    }

    public void setUseDefaultObjectMapper(boolean useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
    }

    public String getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(String objectMapper) {
        this.objectMapper = objectMapper;
    }
}
