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
package org.apache.camel.kotlin.model.rest

import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.ValueDefinition
import org.apache.camel.model.rest.CollectionFormat
import org.apache.camel.model.rest.ParamDefinition
import org.apache.camel.model.rest.RestParamType
import org.apache.camel.model.rest.RestPropertyDefinition

@CamelDslMarker
class ParamDsl(
    val def: ParamDefinition
) {

    fun name(name: String) {
        def.name = name
    }

    fun type(type: RestParamType) {
        def.type = type
    }

    fun description(description: String) {
        def.description = description
    }

    fun defaultValue(defaultValue: String) {
        def.defaultValue = defaultValue
    }

    fun defaultValue(defaultValue: Any) {
        def.defaultValue = defaultValue.toString()
    }

    fun required(required: Boolean) {
        def.required = required
    }

    fun collectionFormat(collectionFormat: CollectionFormat) {
        def.collectionFormat = collectionFormat
    }

    fun arrayType(arrayType: String) {
        def.arrayType = arrayType
    }

    fun dataType(dataType: String) {
        def.dataType = dataType
    }

    fun dataFormat(dataFormat: String) {
        def.dataFormat = dataFormat
    }

    fun allowableValues(allowableValues: List<String>) {
        def.allowableValues = allowableValues.map { ValueDefinition(it) }
    }

    fun allowableValues(vararg allowableValues: String) {
        def.allowableValues = allowableValues.map { ValueDefinition(it) }
    }

    fun allowableValues(allowableValues: String) {
        def.allowableValues = allowableValues.split(",").map { ValueDefinition(it) }
    }

    fun withExample(key: String, value: String) {
        if (def.examples == null) def.examples = mutableListOf()
        def.examples.add(RestPropertyDefinition(key, value))
    }
}