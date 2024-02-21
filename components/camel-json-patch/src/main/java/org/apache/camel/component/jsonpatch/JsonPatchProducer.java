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
package org.apache.camel.component.jsonpatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;

public class JsonPatchProducer extends DefaultProducer {
    private final JsonPatchEndpoint endpoint;
    private final ObjectMapper objectMapper;

    public JsonPatchProducer(JsonPatchEndpoint endpoint) {
        this(endpoint, new ObjectMapper());
    }

    public JsonPatchProducer(JsonPatchEndpoint endpoint, ObjectMapper objectMapper) {
        super(endpoint);
        this.endpoint = endpoint;
        this.objectMapper = objectMapper;
    }

    public void process(Exchange exchange) throws Exception {
        String resourceUri = exchange.getIn().getHeader(JsonPatchConstants.JSON_PATCH_RESOURCE_URI, String.class);
        if (resourceUri == null || resourceUri.isEmpty()) {
            resourceUri = endpoint.getResourceUri();
        }

        final JsonPatch patch = objectMapper.readValue(
                ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), resourceUri),
                JsonPatch.class);
        JsonNode input = objectMapper.readTree(exchange.getIn().getBody(String.class));
        JsonNode result = patch.apply(input);

        ExchangeHelper.setInOutBodyPatternAware(exchange, result.toString());
    }

}
