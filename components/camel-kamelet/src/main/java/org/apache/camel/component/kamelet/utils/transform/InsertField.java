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
package org.apache.camel.component.kamelet.utils.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;
import org.apache.camel.support.LanguageSupport;

public class InsertField implements Processor {

    String field;
    String value;

    /**
     * Default constructor.
     */
    public InsertField() {
    }

    /**
     * Constructor using fields.
     *
     * @param field the field name to insert.
     * @param value the value of the new field.
     */
    public InsertField(String field, String value) {
        this.field = field;
        this.value = value;
    }

    public void process(Exchange ex) throws InvalidPayloadException {
        JsonNode body = ex.getMessage().getBody(JsonNode.class);

        if (body == null) {
            throw new InvalidPayloadException(ex, JsonNode.class);
        }

        String resolvedValue;
        if (LanguageSupport.hasSimpleFunction(value)) {
            resolvedValue = ex.getContext().resolveLanguage("simple").createExpression(value).evaluate(ex, String.class);
        } else {
            resolvedValue = value;
        }

        switch (body.getNodeType()) {
            case ARRAY:
                ((ArrayNode) body).add(resolvedValue);
                break;
            case OBJECT:
                ((ObjectNode) body).put(field, resolvedValue);
                break;
            default:
                ((ObjectNode) body).put(field, resolvedValue);
                break;
        }

        ex.getMessage().setBody(body);
    }

    public void setField(String field) {
        this.field = field;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
