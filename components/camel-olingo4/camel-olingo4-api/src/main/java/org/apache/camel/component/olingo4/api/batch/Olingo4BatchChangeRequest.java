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

import java.util.Map;

/**
 * Batch Change part.
 */
public class Olingo4BatchChangeRequest extends Olingo4BatchRequest {

    protected String contentId;
    protected Operation operation;
    protected Object body;

    public Operation getOperation() {
        return operation;
    }

    public Object getBody() {
        return body;
    }

    public String getContentId() {
        return contentId;
    }

    @Override
    public String toString() {
        return new StringBuilder("Batch Change Request{ ").append(resourceUri).append("/").append(resourcePath).append(", headers=").append(headers).append(", contentId=")
            .append(contentId).append(", operation=").append(operation).append(", body=").append(body).append('}').toString();
    }

    public static Olingo4BatchChangeRequestBuilder resourcePath(String resourcePath) {
        if (resourcePath == null) {
            throw new IllegalArgumentException("resourcePath");
        }
        return new Olingo4BatchChangeRequestBuilder().resourcePath(resourcePath);
    }

    public static class Olingo4BatchChangeRequestBuilder {

        private Olingo4BatchChangeRequest request = new Olingo4BatchChangeRequest();

        public Olingo4BatchChangeRequestBuilder resourcePath(String resourcePath) {
            request.resourcePath = resourcePath;
            return this;
        }

        public Olingo4BatchChangeRequestBuilder resourceUri(String resourceUri) {
            request.resourceUri = resourceUri;
            return this;
        }

        public Olingo4BatchChangeRequestBuilder headers(Map<String, String> headers) {
            request.headers = headers;
            return this;
        }

        public Olingo4BatchChangeRequestBuilder contentId(String contentId) {
            request.contentId = contentId;
            return this;
        }

        public Olingo4BatchChangeRequestBuilder operation(Operation operation) {
            request.operation = operation;
            return this;
        }

        public Olingo4BatchChangeRequestBuilder body(Object body) {
            request.body = body;
            return this;
        }

        public Olingo4BatchChangeRequest build() {
            // avoid later NPEs
            if (request.resourcePath == null) {
                throw new IllegalArgumentException("Null resourcePath");
            }
            if (request.operation == null) {
                throw new IllegalArgumentException("Null operation");
            }
            if (request.operation != Operation.DELETE && request.body == null) {
                throw new IllegalArgumentException("Null body");
            }
            return request;
        }
    }
}
