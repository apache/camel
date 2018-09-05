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
package org.apache.camel.component.yql.client;

public final class YqlResponse {

    private final int status;
    private final String body;
    private final String httpRequest;

    private YqlResponse(final Builder builder) {
        this.status = builder.status;
        this.body = builder.body;
        this.httpRequest = builder.httpRequest;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public String getHttpRequest() {
        return httpRequest;
    }

    static YqlResponse.Builder builder() {
        return new YqlResponse.Builder();
    }

    static class Builder {
        private int status;
        private String body;
        private String httpRequest;

        Builder() {
        }

        Builder status(final int status) {
            this.status = status;
            return this;
        }

        Builder body(final String body) {
            this.body = body;
            return this;
        }

        Builder httpRequest(final String httpRequest) {
            this.httpRequest = httpRequest;
            return this;
        }

        YqlResponse build() {
            return new YqlResponse(this);
        }
    }
}
