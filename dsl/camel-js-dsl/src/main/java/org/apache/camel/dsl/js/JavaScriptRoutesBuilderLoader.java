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
package org.apache.camel.dsl.js;

import java.io.Reader;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StartupStep;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.RoutesBuilderLoaderSupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import static org.graalvm.polyglot.Source.newBuilder;

@ManagedResource(description = "Managed JavaScriptRoutesBuilderLoader")
@RoutesLoader(JavaScriptRoutesBuilderLoader.EXTENSION)
public class JavaScriptRoutesBuilderLoader extends RoutesBuilderLoaderSupport {
    public static final String EXTENSION = "js";
    public static final String LANGUAGE_ID = "js";

    private StartupStepRecorder recorder;

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        if (getCamelContext() != null) {
            this.recorder = getCamelContext().adapt(ExtendedCamelContext.class).getStartupStepRecorder();
        }
    }

    @ManagedAttribute(description = "Supported file extension")
    @Override
    public String getSupportedExtension() {
        return EXTENSION;
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        StartupStep step = recorder != null
                ? recorder.beginStep(JavaScriptRoutesBuilderLoader.class, resource.getLocation(), "Compiling RouteBuilder")
                : null;

        try {
            return EndpointRouteBuilder.loadEndpointRoutesBuilder(resource, this::load);
        } finally {
            if (recorder != null) {
                recorder.endStep(step);
            }
        }
    }

    private void load(Reader reader, EndpointRouteBuilder builder) {
        final Context context = Context.newBuilder(LANGUAGE_ID).allowAllAccess(true).build();
        final Value bindings = context.getBindings(LANGUAGE_ID);

        // configure bindings
        bindings.putMember("__dsl", new JavaScriptDSL(builder));

        //
        // Expose JavaScriptDSL methods to global scope.
        //
        context.eval(
                LANGUAGE_ID,
                String.join(
                        "\n",
                        "Object.setPrototypeOf(globalThis, new Proxy(Object.prototype, {",
                        "    has(target, key) {",
                        "        return key in __dsl || key in target;",
                        "    },",
                        "    get(target, key, receiver) {",
                        "        return Reflect.get((key in __dsl) ? __dsl : target, key, receiver);",
                        "    }",
                        "}));"));

        //
        // Run the script.
        //
        context.eval(
                newBuilder(LANGUAGE_ID, reader, "Unnamed").buildLiteral());

        //
        // Close the polyglot context when the camel context stops
        //
        builder.getContext().addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onContextStopping(CamelContext camelContext) {
                context.close(true);
            }
        });
    }
}
