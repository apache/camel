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
package org.apache.camel.component.olingo2.api.batch;

import java.util.Map;

/**
 * Batch Query part.
 */
public class Olingo2BatchQueryRequest extends Olingo2BatchRequest {

    private Map<String, String> queryParams;

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public static Olingo2BatchQueryRequestBuilder resourcePath(String resourcePath) {
        if (resourcePath == null) {
            throw new IllegalArgumentException("resourcePath");
        }
        return new Olingo2BatchQueryRequestBuilder().resourcePath(resourcePath);
    }

    @Override
    public String toString() {
        return new StringBuilder("Batch Query Request{ ").append(resourcePath).append(", headers=").append(headers).append(", queryParams=").append(queryParams).append('}')
            .toString();
    }

    public static class Olingo2BatchQueryRequestBuilder {
        private Olingo2BatchQueryRequest request = new Olingo2BatchQueryRequest();

        public Olingo2BatchQueryRequest build() {
            // avoid later NPEs
            if (request.resourcePath == null) {
                throw new IllegalArgumentException("Null resourcePath");
            }
            return request;
        }

        public Olingo2BatchQueryRequestBuilder resourcePath(String resourcePath) {
            request.resourcePath = resourcePath;
            return this;
        }

        public Olingo2BatchQueryRequestBuilder headers(Map<String, String> headers) {
            request.headers = headers;
            return this;
        }

        public Olingo2BatchQueryRequestBuilder queryParams(Map<String, String> queryParams) {
            request.queryParams = queryParams;
            return this;
        }
    }
}
