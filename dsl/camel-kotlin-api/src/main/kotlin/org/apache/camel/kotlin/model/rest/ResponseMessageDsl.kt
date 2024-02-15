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
import org.apache.camel.model.rest.ResponseHeaderDefinition
import org.apache.camel.model.rest.ResponseMessageDefinition
import org.apache.camel.model.rest.RestPropertyDefinition

@CamelDslMarker
class ResponseMessageDsl(
    val def: ResponseMessageDefinition
) {

    fun code(code: Int) {
        def.code = code.toString()
    }

    fun code(code: String) {
        def.code = code
    }

    fun description(description: String) {
        def.message = description
    }

    fun responseModel(responseModel: String) {
        def.responseModel = responseModel
    }

    fun withHeader(i: ResponseHeaderDsl.() -> Unit) {
        if (def.headers == null) def.headers = mutableListOf()
        val headerDef = ResponseHeaderDefinition(def)
        ResponseHeaderDsl(headerDef).apply(i)
        def.headers.add(headerDef)
    }

    fun withExample(key: String, value: String) {
        if (def.examples == null) def.examples = mutableListOf()
        def.examples.add(RestPropertyDefinition(key, value))
    }
}