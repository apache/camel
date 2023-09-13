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
package org.apache.camel.dsl.kotlin

import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm

class KotlinCompilationConfiguration : ScriptCompilationConfiguration(
{
    defaultImports(
        "org.apache.camel",
        "org.apache.camel.spi"
    )

    jvm {
        //
        // The Kotlin script compiler does not inherit
        // the classpath by default.
        //
        dependenciesFromClassloader(wholeClasspath = true)

        //
        // Scripts have to be compiled with the same
        // jvm target level as the loader.
        //
        compilerOptions.append("-jvm-target")
        compilerOptions.append(JVM_TARGET)

        //
        // We may remove when https://youtrack.jetbrains.com/issue/KT-57907 is solved
        //
        compilerOptions.append("-Xadd-modules=ALL-MODULE-PATH")
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
})