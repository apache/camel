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

import java.io.Serial;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import org.apache.camel.Exchange;
import org.apache.camel.ValidationException;

public class JsonValidationException extends ValidationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final JsonSchema schema;
    private final Set<ValidationMessage> errors;

    public JsonValidationException(Exchange exchange, JsonSchema schema, Set<ValidationMessage> errors) {
        super(exchange, "JSON validation error with " + errors.size() + " errors:\n" + toString(errors));
        this.schema = schema;
        this.errors = errors;
    }

    public JsonValidationException(Exchange exchange, JsonSchema schema, Exception e) {
        super(e.getMessage(), exchange, e);
        this.schema = schema;
        this.errors = Collections.emptySet();
    }

    public JsonSchema getSchema() {
        return schema;
    }

    public Set<ValidationMessage> getErrors() {
        return errors;
    }

    public int getNumberOfErrors() {
        return errors.size();
    }

    private static String toString(Set<ValidationMessage> errors) {
        return errors.stream().map(ValidationMessage::toString).collect(Collectors.joining("\n"));
    }
}
