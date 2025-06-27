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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;

public class DropField implements Processor {

    String field;

    /**
     * Default constructor.
     */
    public DropField() {
    }

    /**
     * Constructor using fields.
     *
     * @param field the field name to drop.
     */
    public DropField(String field, String value) {
        this.field = field;
    }

    public void process(Exchange ex) throws InvalidPayloadException {
        JsonNode body = ex.getMessage().getBody(JsonNode.class);
        if (body == null) {
            throw new InvalidPayloadException(ex, JsonNode.class);
        }

        if (body.getNodeType().equals(JsonNodeType.OBJECT)) {
            ((ObjectNode) body).remove(field);
            ex.getMessage().setBody(body);
        }
    }

    public void setField(String field) {
        this.field = field;
    }
}
