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

package org.apache.camel.component.rest.openapi;

import io.swagger.v3.oas.models.Operation;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.RestBindingAdvice;

class RestOpenApiConsumerPath implements RestConsumerContextPathMatcher.ConsumerPath<Operation> {

    private final String verb;
    private final String path;
    private final Operation consumer;
    private final RestBindingAdvice binding;

    public RestOpenApiConsumerPath(String verb, String path, Operation consumer, RestBindingAdvice binding) {
        this.verb = verb;
        this.path = path;
        this.consumer = consumer;
        this.binding = binding;
    }

    @Override
    public String getRestrictMethod() {
        return verb;
    }

    @Override
    public String getConsumerPath() {
        return path;
    }

    @Override
    public Operation getConsumer() {
        return consumer;
    }

    @Override
    public boolean isMatchOnUriPrefix() {
        return false;
    }

    public RestBindingAdvice getBinding() {
        return binding;
    }
}
