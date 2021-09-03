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
package org.apache.camel.dsl.groovy.common.model

import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestDefinition

class RestVerbConfiguration {
    protected final RouteBuilder builder
    protected final RestDefinition definition

    RestVerbConfiguration(RouteBuilder builder, String path) {
        this.builder = builder
        this.definition = builder.rest(path)
    }

    def get(String path, @DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.get(path)
        callable.call()
    }

    def get(@DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.get()
        callable.call()
    }

    def post(String path, @DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.post(path)
        callable.call()
    }

    def post(@DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.post()
        callable.call()
    }

    def delete(String path, @DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.delete(path)
        callable.call()
    }

    def delete(@DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.delete()
        callable.call()
    }

    def head(String path, @DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.head(path)
        callable.call()
    }

    def head(@DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.head()
        callable.call()
    }

    def put(String path, @DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.put(path)
        callable.call()
    }

    def put(@DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.put()
        callable.call()
    }

    def patch(String path, @DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.patch(path)
        callable.call()
    }

    def patch(@DelegatesTo(RestDefinition) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = definition.patch()
        callable.call()
    }
}
