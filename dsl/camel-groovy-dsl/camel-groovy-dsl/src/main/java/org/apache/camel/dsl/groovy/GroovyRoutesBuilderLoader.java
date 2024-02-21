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
package org.apache.camel.dsl.groovy;

import java.io.Reader;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.DelegatingScript;
import org.apache.camel.Experimental;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.dsl.groovy.common.GroovyDSL;
import org.apache.camel.endpointdsl.support.EndpointRouteBuilderLoaderSupport;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

@Experimental
@ManagedResource(description = "Managed GroovyRoutesBuilderLoader")
@RoutesLoader(GroovyRoutesBuilderLoader.EXTENSION)
public class GroovyRoutesBuilderLoader extends EndpointRouteBuilderLoaderSupport {
    public static final String EXTENSION = "groovy";

    public GroovyRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    protected void doLoadEndpointRouteBuilder(Reader reader, EndpointRouteBuilder builder) {
        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports("org.apache.camel");
        ic.addStarImports("org.apache.camel.spi");

        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(ic);
        cc.setScriptBaseClass(DelegatingScript.class.getName());

        ClassLoader cl = builder.getContext().getApplicationContextClassLoader() != null
                ? builder.getContext().getApplicationContextClassLoader()
                : Thread.currentThread().getContextClassLoader();

        GroovyShell sh = new GroovyShell(cl, new Binding(), cc);
        DelegatingScript script = (DelegatingScript) sh.parse(reader);

        // set the delegate target
        script.setDelegate(new GroovyDSL(builder));
        script.run();
    }
}
