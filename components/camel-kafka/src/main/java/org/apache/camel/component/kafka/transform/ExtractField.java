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
package org.apache.camel.component.kafka.transform;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;

public class ExtractField implements Processor {

    String field;
    String headerOutputName;
    boolean headerOutput;
    boolean strictHeaderCheck;
    boolean trimField;

    static final String EXTRACTED_FIELD_HEADER = "CamelKameletsExtractFieldName";

    /**
     * Default constructor
     */
    public ExtractField() {
    }

    /**
     * Constructor using field member.
     *
     * @param field the field name to extract.
     */
    public ExtractField(String field) {
        this.field = field;
    }

    @Override
    public void process(Exchange ex) throws InvalidPayloadException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNodeBody = ex.getMessage().getBody(JsonNode.class);

        if (jsonNodeBody == null) {
            throw new InvalidPayloadException(ex, JsonNode.class);

        }

        Map<Object, Object> body = mapper.convertValue(jsonNodeBody, new TypeReference<Map<Object, Object>>() {
        });
        if (!headerOutput || (strictHeaderCheck && checkHeaderExistence(ex))) {
            ex.getMessage().setBody(body.get(field));
        } else {
            extractToHeader(ex, body);
        }
        if (trimField) {
            ex.setProperty("trimField", "true");
        } else {
            ex.setProperty("trimField", "false");
        }
    }

    private void extractToHeader(Exchange ex, Map<Object, Object> body) {
        if (headerOutputName == null || headerOutputName.isEmpty() || "none".equalsIgnoreCase(headerOutputName)) {
            ex.getMessage().setHeader(EXTRACTED_FIELD_HEADER, body.get(field));
        } else {
            ex.getMessage().setHeader(headerOutputName, body.get(field));
        }
    }

    private boolean checkHeaderExistence(Exchange exchange) {
        if (headerOutputName == null || headerOutputName.isEmpty() || "none".equalsIgnoreCase(headerOutputName)) {
            return exchange.getMessage().getHeaders().containsKey(EXTRACTED_FIELD_HEADER);
        } else {
            return exchange.getMessage().getHeaders().containsKey(headerOutputName);
        }
    }

    public void setField(String field) {
        this.field = field;
    }

    public void setHeaderOutput(boolean headerOutput) {
        this.headerOutput = headerOutput;
    }

    public void setHeaderOutputName(String headerOutputName) {
        this.headerOutputName = headerOutputName;
    }

    public void setStrictHeaderCheck(boolean strictHeaderCheck) {
        this.strictHeaderCheck = strictHeaderCheck;
    }

    public void setTrimField(boolean trimField) {
        this.trimField = trimField;
    }
}
