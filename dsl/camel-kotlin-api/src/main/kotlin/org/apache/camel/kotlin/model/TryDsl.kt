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
package org.apache.camel.kotlin.model

import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.StepsDsl
import org.apache.camel.model.CatchDefinition
import org.apache.camel.model.FinallyDefinition
import org.apache.camel.model.TryDefinition
import kotlin.reflect.KClass

@CamelDslMarker
class TryDsl(
    val def: TryDefinition
) : OptionalIdentifiedDsl(def) {

    fun steps(i: StepsDsl.() -> Unit) {
        StepsDsl(def).apply(i)
        def.endDoTry()
    }

    fun doCatch(vararg doCatch: KClass<Exception>, i: CatchDsl.() -> Unit) {
        val catchDef = CatchDefinition()
        catchDef.exception(*doCatch.map { it.java }.toTypedArray())
        CatchDsl(catchDef).apply(i)
        def.catchClauses.add(catchDef)
    }

    fun doFinally(i: StepsDsl.() -> Unit) {
        val finallyDef = FinallyDefinition()
        StepsDsl(finallyDef).apply(i)
        def.finallyClause = finallyDef
    }
}