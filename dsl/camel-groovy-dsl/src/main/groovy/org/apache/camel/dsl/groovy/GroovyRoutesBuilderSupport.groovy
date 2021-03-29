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
package org.apache.camel.dsl.groovy

import org.apache.camel.builder.endpoint.EndpointRouteBuilder
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

class GroovyRoutesBuilderSupport {
    private GroovyRoutesBuilderSupport() {
    }

    static void load(Reader reader, EndpointRouteBuilder builder) {
        def ic = new ImportCustomizer()
        ic.addStarImports('org.apache.camel')
        ic.addStarImports('org.apache.camel.spi')

        def cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(ic)
        cc.setScriptBaseClass(DelegatingScript.class.getName())

        def cl = builder.context.applicationContextClassLoader ?: Thread.currentThread().getContextClassLoader()
        def sh = new GroovyShell(cl, new Binding(), cc)
        def script = (DelegatingScript) sh.parse(reader)

        // set the delegate target
        script.setDelegate(new GroovyDSL(builder))
        script.run()
    }
}
