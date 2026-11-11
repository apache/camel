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
package org.apache.camel.component.aws2.bedrock;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Helper for the message body of the Bedrock producers.
 */
public final class BedrockBodyHelper {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private BedrockBodyHelper() {
    }

    /**
     * The message body marshalled to JSON when it is a Map or a Jackson JsonNode, or null when the body is neither, in
     * which case the caller converts it as usual.
     */
    public static String jsonBody(Exchange exchange) throws InvalidPayloadException {
        Object body = exchange.getMessage().getBody();
        if (body instanceof Map || body instanceof JsonNode) {
            try {
                return MAPPER.writeValueAsString(body);
            } catch (JacksonException e) {
                throw new InvalidPayloadException(exchange, String.class, exchange.getMessage(), e);
            }
        }
        return null;
    }
}
