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

import org.apache.camel.Experimental
import org.apache.camel.RuntimeCamelException
import org.apache.camel.api.management.ManagedResource
import org.apache.camel.builder.endpoint.EndpointRouteBuilder
import org.apache.camel.endpointdsl.support.EndpointRouteBuilderLoaderSupport
import org.apache.camel.spi.annotations.RoutesLoader
import org.slf4j.LoggerFactory
import java.io.Reader
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

@Experimental
@ManagedResource(description = "Managed KotlinRoutesBuilderLoader")
@RoutesLoader(EXTENSION)
class KotlinRoutesBuilderLoader : org.apache.camel.endpointdsl.support.EndpointRouteBuilderLoaderSupport(EXTENSION) {
    @Throws(Exception::class)
    override fun doLoadEndpointRouteBuilder(reader: Reader, builder: EndpointRouteBuilder) {
        val host = BasicJvmScriptingHost()
        val config = createJvmCompilationConfigurationFromTemplate<KotlinDSL>()

        val result = host.eval(
                reader.readText().toScriptSource(),
                config,
                ScriptEvaluationConfiguration {
                    //
                    // Arguments used to initialize the script base class (IntegrationConfiguration)
                    //
                    constructorArgs(builder)
                }
        )

        // ensure evaluation errors propagation
        when(val rv = result.valueOrNull()?.returnValue) {
            is ResultValue.Error -> throw RuntimeCamelException(rv.error)
            else -> {} // result is okay
        }

        if (result.reports.isNotEmpty()) {
            val logger = LoggerFactory.getLogger(KotlinRoutesBuilderLoader::class.java)
            for (report in result.reports) {
                when (report.severity) {
                    ScriptDiagnostic.Severity.FATAL -> logger.error(report.message, report.exception)
                    ScriptDiagnostic.Severity.ERROR -> logger.error(report.message, report.exception)
                    ScriptDiagnostic.Severity.WARNING -> logger.warn(report.message, report.exception)
                    ScriptDiagnostic.Severity.INFO -> logger.info(report.message)
                    ScriptDiagnostic.Severity.DEBUG -> logger.debug(report.message)
                }
            }
        }
    }
}