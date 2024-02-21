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
import org.apache.camel.model.rest.*
import java.util.*

@CamelDslMarker
class RestDsl(
    val def: RestDefinition
) {

    fun get(get: String? = null, i: RestVerbDsl.() -> Unit) {
        verb("get", get, i)
    }

    fun post(post: String? = null, i: RestVerbDsl.() -> Unit) {
        verb("post", post, i)
    }

    fun put(put: String? = null, i: RestVerbDsl.() -> Unit) {
        verb("put", put, i)
    }

    fun patch(patch: String? = null, i: RestVerbDsl.() -> Unit) {
        verb("patch", patch, i)
    }

    fun delete(delete: String? = null, i: RestVerbDsl.() -> Unit) {
        verb("delete", delete, i)
    }

    fun head(head: String? = null, i: RestVerbDsl.() -> Unit) {
        verb("head", head, i)
    }

    fun verb(verb: String, uri: String? = null, i: RestVerbDsl.() -> Unit) {
        val answer = when (verb) {
            "get" -> GetDefinition()
            "post" -> PostDefinition()
            "delete" -> DeleteDefinition()
            "head" -> HeadDefinition()
            "put" -> PutDefinition()
            "patch" -> PatchDefinition()
            else -> throw IllegalArgumentException("Verb $verb not supported")
        }
        def.verbs.add(answer)
        answer.rest = def
        answer.path = uri
        RestVerbDsl(answer).apply(i)
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

    fun tag(tag: String) {
        def.tag = tag
    }

    fun securityDefinitions(i: RestSecuritiesDsl.() -> Unit) {
        if (def.securityDefinitions == null) def.securityDefinitions = RestSecuritiesDefinition(def)
        RestSecuritiesDsl(def.securityDefinitions, def).apply(i)
    }

    fun securityRequirements(i: SecuritiesDsl.() -> Unit) {
        if (def.securityRequirements == null) def.securityRequirements = mutableListOf()
        SecuritiesDsl(def.securityRequirements).apply(i)
    }
}