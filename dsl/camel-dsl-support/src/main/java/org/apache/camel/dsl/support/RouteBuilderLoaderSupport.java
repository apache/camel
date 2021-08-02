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
package org.apache.camel.dsl.support;

import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StartupStep;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteBuilderLifecycleStrategy;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.RoutesBuilderLoaderSupport;

/**
 * Base class for {@link RoutesBuilderLoader} implementations.
 */
public abstract class RouteBuilderLoaderSupport extends RoutesBuilderLoaderSupport {
    private final String extension;

    private StartupStepRecorder recorder;

    protected RouteBuilderLoaderSupport(String extension) {
        this.extension = extension;
    }

    @ManagedAttribute(description = "Supported file extension")
    @Override
    public String getSupportedExtension() {
        return extension;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        if (getCamelContext() != null) {
            this.recorder = getCamelContext().adapt(ExtendedCamelContext.class).getStartupStepRecorder();
        }
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        final RouteBuilder builder = doLoadRouteBuilder(resource);
        CamelContextAware.trySetCamelContext(builder, getCamelContext());

        if (recorder != null) {
            StartupStep step = recorder.beginStep(
                    getClass(),
                    resource.getLocation(),
                    "Loading route from: " + resource.getLocation());

            builder.addLifecycleInterceptor(new RouteBuilderLifecycleStrategy() {
                @Override
                public void afterConfigure(RouteBuilder builder) {
                    step.endStep();
                }
            });
        }

        return builder;
    }

    protected abstract RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception;
}
