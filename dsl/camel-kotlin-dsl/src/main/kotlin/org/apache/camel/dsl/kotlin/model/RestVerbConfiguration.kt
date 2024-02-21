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
package org.apache.camel.dsl.kotlin.model

import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestDefinition

open class RestVerbConfiguration(
        private val builder: RouteBuilder,
        private val definition: RestDefinition) {

    constructor(builder: RouteBuilder, path: String): this(builder, builder.rest(path))

    fun get(path: String, block: RestDefinition.() -> Unit) = definition.get(path).block()
    fun get(block: RestDefinition.() -> Unit) = definition.get().block()

    fun post(path: String, block: RestDefinition.() -> Unit) = definition.post(path).block()
    fun post(block: RestDefinition.() -> Unit) = definition.post().block()

    fun delete(path: String, block: RestDefinition.() -> Unit) = definition.delete(path).block()
    fun delete(block: RestDefinition.() -> Unit) = definition.delete().block()

    fun head(path: String, block: RestDefinition.() -> Unit) = definition.head(path).block()
    fun head(block: RestDefinition.() -> Unit) = definition.head().block()

    fun put(path: String, block: RestDefinition.() -> Unit) = definition.put(path).block()
    fun put(block: RestDefinition.() -> Unit) = definition.put().block()

    fun patch(path: String, block: RestDefinition.() -> Unit) = definition.patch(path).block()
    fun patch(block: RestDefinition.() -> Unit) = definition.patch().block()
}