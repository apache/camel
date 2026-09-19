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
package org.apache.camel.dsl.yaml.support.model

import java.time.Duration

/**
 * A bean with no public constructor that is created through its builder, in the shape of a LangChain4j model
 * (CAMEL-24820): a static builder() and a nested builder with fluent setters and build().
 */
final class MyBuiltBean {
    final String baseUrl
    final String modelName
    final Duration timeout
    String label

    private MyBuiltBean(Builder b) {
        this.baseUrl = b.baseUrl
        this.modelName = b.modelName
        this.timeout = b.timeout
    }

    static Builder builder() {
        return new Builder()
    }

    static final class Builder {
        private String baseUrl
        private String modelName
        private Duration timeout = Duration.ofSeconds(60)

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl
            return this
        }

        Builder modelName(String modelName) {
            this.modelName = modelName
            return this
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout
            return this
        }

        MyBuiltBean build() {
            return new MyBuiltBean(this)
        }
    }
}
