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
package org.apache.camel.dsl.jsh;

import java.io.Reader;

import javax.script.ScriptException;

import jdk.jshell.JShell;
import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import org.apache.camel.CamelContext;
import org.apache.camel.Experimental;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.RoutesBuilderLoaderSupport;
import org.apache.camel.util.IOHelper;

/**
 * A {@link RoutesBuilderLoader} implementation based on {@link JShell}.
 */
@ManagedResource(description = "Managed JShell RoutesBuilderLoader")
@Experimental
@RoutesLoader("jsh")
public class JshRoutesBuilderLoader extends RoutesBuilderLoaderSupport {
    public static final String EXTENSION = "jsh";

    @ManagedAttribute(description = "Supported file extension")
    @Override
    public String getSupportedExtension() {
        return EXTENSION;
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        return EndpointRouteBuilder.loadEndpointRoutesBuilder(resource, JshRoutesBuilderLoader::eval);
    }

    private static void eval(Reader reader, EndpointRouteBuilder builder) throws Exception {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        final String content = IOHelper.toString(reader);

        //
        // By default the jdk.jshell.execution.DefaultLoaderDelegate uses a
        // custom URL class-loader and does not provide any option to set the
        // parent which causes the ThreadLocal hack used to inject bindings
        // to fail as there are two copies of the JSH class (one from the
        // Quarkus class loader and one for the custom one).
        //
        final JshClassLoader jshcl = new JshClassLoader(tccl);
        final LoaderDelegate delegate = new JshLoaderDelegate(jshcl);
        final ExecutionControl control = new DirectExecutionControl(delegate);
        final ExecutionControlProvider provider = Jsh.wrapExecutionControl("jsh-direct", control);

        Thread.currentThread().setContextClassLoader(jshcl);

        //
        // Leverage DirectExecutionControl as execution engine to make JShell running
        // in the current process and give a chance to bind variables to the script
        // using ThreadLocal hack.
        //
        try (JShell jshell = JShell.builder().executionEngine(provider, null).build()) {
            //
            // since we can't set a base class for the snippet as we do for other
            // languages (groovy, kotlin) we need to introduce a top level variable
            // that users need to use to access the RouteBuilder, like:
            //
            //     builder.from("timer:tick")
            //         .to("log:info")
            //
            // context and thus registry can easily be retrieved from the registered
            // variable `builder` but for a better UX, add them as top level vars.
            //
            Jsh.setBinding(jshell, "builder", builder, EndpointRouteBuilder.class);
            Jsh.setBinding(jshell, "context", builder.getContext(), CamelContext.class);
            Jsh.setBinding(jshell, "registry", builder.getContext().getRegistry(), Registry.class);

            for (String snippet : Jsh.compile(jshell, content)) {
                Jsh.eval(jshell, snippet);
            }
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        } finally {
            // remove contextual bindings once the snippet has been evaluated
            Jsh.clearBindings();
            // restore original TCCL
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }
}
