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
package org.apache.camel.dsl.groovy.common.model

import org.apache.camel.CamelContext

class CamelConfiguration {
    private final CamelContext context

    CamelConfiguration(CamelContext context) {
        this.context = context
    }

    def components(@DelegatesTo(ComponentsConfiguration) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = new ComponentsConfiguration(context)
        callable.call()
    }

    def dataFormats(@DelegatesTo(DataFormatsConfiguration) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = new DataFormatsConfiguration(context)
        callable.call()
    }

    def languages(@DelegatesTo(LanguagesConfiguration) Closure<?> callable) {
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.delegate = new LanguagesConfiguration(context)
        callable.call()
    }
}
