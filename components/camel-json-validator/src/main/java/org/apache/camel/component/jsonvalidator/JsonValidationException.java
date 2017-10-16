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
package org.apache.camel.component.jsonvalidator;

import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.ValidationException;
import org.everit.json.schema.Schema;

public class JsonValidationException extends ValidationException {
    
    private static final long serialVersionUID = 1L;
    
    public JsonValidationException(Exchange exchange, Schema schema, org.everit.json.schema.ValidationException e) {
        super(e.getAllMessages().stream().collect(Collectors.joining(", ")), exchange, e);
    }

    public JsonValidationException(Exchange exchange, Schema schema, Exception e) {
        super(e.getMessage(), exchange, e);
    }
}
