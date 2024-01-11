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
import org.apache.camel.model.rest.RestDefinition

@CamelDslMarker
class RestVerbDsl(
    val def: RestDefinition
) {

    fun consumes(consumes: String) {
        def.consumes(consumes)
    }

    fun produces(produces: String) {
        def.produces(produces)
    }

    fun to(i: UriDsl.() -> Unit) {
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        def.to(uri)
    }
}