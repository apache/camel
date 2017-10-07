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

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.everit.json.schema.FormatValidator;
import org.everit.json.schema.Schema;

/**
 * Can be used to create custom schema for the JSON validator endpoint.
 * This interface is useful to add custom {@link FormatValidator} to the {@link Schema}
 * 
 * For more information see 
 * <a href="https://github.com/everit-org/json-schema#format-validators">Format Validators</a>
 * in the Everit JSON Schema documentation. 
 */
public interface JsonSchemaLoader {
    
    /**
     * Create a new Schema based on the schema input stream.
     *
     * @param camelContext camel context
     * @param schemaInputStream the resource input stream
     * @return a Schema to be used when validating incoming requests
     */
    Schema createSchema(CamelContext camelContext, InputStream schemaInputStream) throws Exception;

}
