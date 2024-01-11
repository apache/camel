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
package org.apache.camel.kotlin

@CamelDslMarker
class UriDsl {

    private var component: String = ""
    private var url: String = ""
    private val properties: MutableMap<String, String> = mutableMapOf()

    fun component(component: String) {
        this.component = component
    }

    fun url(url: String) {
        this.url = url
    }

    fun property(property: String, value: String) {
        properties[property] = value
    }

    internal fun toUri(): String {
        val sb = StringBuilder()
        sb.append(component)
        if (component != "") {
            sb.append(":")
        }
        sb.append(url)
        var first = '?'
        for (property in properties) {
            sb.append(first)
            first = '&'
            sb.append(property.key)
            sb.append('=')
            sb.append(property.value)
        }
        return sb.toString()
    }
}