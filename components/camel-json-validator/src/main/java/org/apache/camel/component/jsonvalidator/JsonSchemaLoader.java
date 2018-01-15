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

import com.networknt.schema.JsonSchema;
import org.apache.camel.CamelContext;

/**
 * Can be used to create custom schema for the JSON validator endpoint.
 */
public interface JsonSchemaLoader {
    
    /**
     * Create a new Schema based on the schema input stream.
     *
     * @param camelContext camel context
     * @param inputStream the resource input stream
     * @return a Schema to be used when validating incoming requests
     */
    JsonSchema createSchema(CamelContext camelContext, InputStream inputStream) throws Exception;

}
