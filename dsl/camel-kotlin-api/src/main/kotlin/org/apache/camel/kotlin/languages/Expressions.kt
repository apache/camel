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
package org.apache.camel.kotlin.languages

import org.apache.camel.Expression
import org.apache.camel.builder.ExpressionBuilder
import org.apache.camel.builder.ValueBuilder
import org.apache.camel.model.PropertyDefinition

fun body(): Expression {
    return ExpressionBuilder.bodyExpression()
}

fun Expression.which(): ValueBuilder {
    return ValueBuilder(this)
}

fun XpathLanguageDsl.namespace(namespace: Map<String, String>) {
    this.namespace(namespace.entries.map { PropertyDefinition(it.key, it.value) }.toMutableList())
}

fun XqueryLanguageDsl.namespace(namespace: Map<String, String>) {
    this.namespace(namespace.entries.map { PropertyDefinition(it.key, it.value) }.toMutableList())
}