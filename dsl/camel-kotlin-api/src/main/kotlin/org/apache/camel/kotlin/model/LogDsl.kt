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

import org.apache.camel.LoggingLevel
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.LogDefinition
import org.slf4j.Logger

@CamelDslMarker
class LogDsl(
    val def: LogDefinition
) : OptionalIdentifiedDsl(def) {

    fun message(message: String) {
        def.message = message
    }

    fun loggingLevel(loggingLevel: LoggingLevel) {
        def.loggingLevel = loggingLevel.name
    }

    fun loggingLevel(loggingLevel: String) {
        def.loggingLevel = loggingLevel
    }

    fun logName(logName: String) {
        def.logName = logName
    }

    fun marker(marker: String) {
        def.marker = marker
    }

    fun logger(logger: Logger) {
        def.setLogger(logger)
    }

    fun logger(logger: String) {
        def.logger = logger
    }
}