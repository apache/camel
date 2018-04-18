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
package org.apache.camel.component.olingo4.api.batch;

import java.util.HashMap;
import java.util.Map;

/**
 * Base part in a multipart Batch request.
 */
public abstract class Olingo4BatchRequest {

    protected String resourceUri;
    protected String resourcePath;
    protected Map<String, String> headers = new HashMap<>();

    public String getResourceUri() {
        return resourceUri;
    }
    
    public String getResourcePath() {
        return resourcePath;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public abstract String toString();
}