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
package org.apache.camel.component.rest.postman;

import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.RestBindingAdvice;

/**
 * One servable request of the collection, as the context path matcher sees it.
 */
class RestPostmanConsumerPath implements RestConsumerContextPathMatcher.ConsumerPath<PostmanRequestBinding> {

    private final String verb;
    private final String path;
    private final PostmanRequestBinding consumer;
    private final RestBindingAdvice binding;
    private final String dispatchId;

    RestPostmanConsumerPath(String verb, String path, PostmanRequestBinding consumer, RestBindingAdvice binding,
                            String dispatchId) {
        this.verb = verb;
        this.path = path;
        this.consumer = consumer;
        this.binding = binding;
        this.dispatchId = dispatchId;
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
    public PostmanRequestBinding getConsumer() {
        return consumer;
    }

    @Override
    public boolean isMatchOnUriPrefix() {
        return false;
    }

    public RestBindingAdvice getBinding() {
        return binding;
    }

    /**
     * The {@code direct} endpoint name this request routes to, resolved at startup against the routes that exist.
     */
    public String getDispatchId() {
        return dispatchId;
    }
}
