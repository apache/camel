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
import org.apache.camel.kotlin.UriDsl
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.rest.RestBindingMode
import org.apache.camel.model.rest.VerbDefinition
import java.util.*
import kotlin.reflect.KClass

@CamelDslMarker
class RestVerbDsl(
    val def: VerbDefinition
) {

    fun params(i: ParamsDsl.() -> Unit) {
        ParamsDsl(def.params, def).apply(i)
    }

    fun responses(i: ResponsesDsl.() -> Unit) {
        if (def.responseMsgs == null) def.responseMsgs = mutableListOf()
        ResponsesDsl(def.responseMsgs, def).apply(i)
    }

    fun security(i: SecuritiesDsl.() -> Unit) {
        SecuritiesDsl(def.security).apply(i)
    }

    fun consumes(consumes: String) {
        def.consumes = consumes
    }

    fun produces(produces: String) {
        def.produces = produces
    }

    fun disabled(disabled: Boolean) {
        def.disabled = disabled.toString()
    }

    fun disabled(disabled: String) {
        def.disabled = disabled
    }

    fun type(type: KClass<*>) {
        def.typeClass = type.java
    }

    fun type(type: String) {
        def.type = type
    }

    fun outType(outType: KClass<*>) {
        def.outTypeClass = outType.java
    }

    fun outType(outType: String) {
        def.outType = outType
    }

    fun bindingMode(bindingMode: RestBindingMode) {
        bindingMode(bindingMode.name)
    }

    fun bindingMode(bindingMode: String) {
        def.bindingMode = bindingMode.lowercase(Locale.getDefault())
    }

    fun skipBindingOnErrorCode(skipBindingOnErrorCode: Boolean) {
        def.skipBindingOnErrorCode = skipBindingOnErrorCode.toString()
    }

    fun skipBindingOnErrorCode(skipBindingOnErrorCode: String) {
        def.skipBindingOnErrorCode = skipBindingOnErrorCode
    }

    fun clientRequestValidation(clientRequestValidation: Boolean) {
        def.clientRequestValidation = clientRequestValidation.toString()
    }

    fun clientRequestValidation(clientRequestValidation: String) {
        def.clientRequestValidation = clientRequestValidation
    }

    fun enableCORS(enableCORS: Boolean) {
        def.enableCORS = enableCORS.toString()
    }

    fun enableCORS(enableCORS: String) {
        def.enableCORS = enableCORS
    }

    fun enableNoContentResponse(enableNoContentResponse: Boolean) {
        def.enableNoContentResponse = enableNoContentResponse.toString()
    }

    fun enableNoContentResponse(enableNoContentResponse: String) {
        def.enableNoContentResponse = enableNoContentResponse
    }

    fun apiDocs(apiDocs: Boolean) {
        def.apiDocs = apiDocs.toString()
    }

    fun apiDocs(apiDocs: String) {
        def.apiDocs = apiDocs
    }

    fun deprecated(deprecated: Boolean) {
        def.deprecated = deprecated.toString()
    }

    fun deprecated(deprecated: String) {
        def.deprecated = deprecated
    }

    fun routeId(routeId: String) {
        def.routeId = routeId
    }

    fun to(i: UriDsl.() -> Unit) {
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        def.to = ToDefinition(uri)
    }
}