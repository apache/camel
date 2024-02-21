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
package org.apache.camel.component.knative.http.assertions;

import java.util.Objects;

import io.vertx.core.http.HttpServerRequest;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.AssertionsForClassTypes;

public class HttpServerRequestAssert extends AbstractAssert<HttpServerRequestAssert, HttpServerRequest> {
    public HttpServerRequestAssert(HttpServerRequest request) {
        super(request, HttpServerRequest.class);
    }

    public static HttpServerRequestAssert assertThat(HttpServerRequest actual) {
        return new HttpServerRequestAssert(actual);
    }

    public AbstractStringAssert<?> header(String name) {
        isNotNull();

        return AssertionsForClassTypes.assertThat(actual.getHeader(name));
    }

    public HttpServerRequestAssert hasHeader(String name) {
        isNotNull();

        if (Objects.isNull(actual.getHeader(name))) {
            failWithMessage("Expected header %s not present", name);
        }

        return this;
    }

    public HttpServerRequestAssert hasHeader(String name, String value) {
        isNotNull();

        if (Objects.isNull(actual.getHeader(name))) {
            failWithMessage("Expected header %s not present", name);
        }

        if (Objects.equals(actual.getHeader(name), value)) {
            failWithMessage("Expected header %s to be <%s> but was <%s>", name, value, actual.getHeader(name));
        }

        return this;
    }
}
