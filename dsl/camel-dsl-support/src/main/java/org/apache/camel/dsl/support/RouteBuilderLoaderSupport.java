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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StartupStep;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteBuilderLifecycleStrategy;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.RoutesBuilderLoaderSupport;

/**
 * Base class for {@link RoutesBuilderLoader} implementations.
 */
public abstract class RouteBuilderLoaderSupport extends RoutesBuilderLoaderSupport {
    private final String extension;
    private final List<CompilePostProcessor> compilePostProcessors = new ArrayList<>();
    private StartupStepRecorder recorder;
    private SourceLoader sourceLoader = new DefaultSourceLoader();

    protected RouteBuilderLoaderSupport(String extension) {
        this.extension = extension;
    }

    @ManagedAttribute(description = "Supported file extension")
    @Override
    public String getSupportedExtension() {
        return extension;
    }

    @ManagedOperation(description = "Is the file extension supported by this route loader")
    @Override
    public boolean isSupportedExtension(String extension) {
        return super.isSupportedExtension(extension);
    }

    /**
     * Gets the registered {@link CompilePostProcessor}.
     */
    public List<CompilePostProcessor> getCompilePostProcessors() {
        return compilePostProcessors;
    }

    /**
     * Add a custom {@link CompilePostProcessor} to handle specific post-processing after compiling the source into a
     * Java object.
     */
    public void addCompilePostProcessor(CompilePostProcessor preProcessor) {
        this.compilePostProcessors.add(preProcessor);
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        if (getCamelContext() != null) {
            this.recorder = getCamelContext().getCamelContextExtension().getStartupStepRecorder();
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getCamelContext() != null) {
            // discover optional compile post-processors to be used
            Set<CompilePostProcessor> pres = getCamelContext().getRegistry().findByType(CompilePostProcessor.class);
            if (pres != null && !pres.isEmpty()) {
                for (CompilePostProcessor pre : pres) {
                    addCompilePostProcessor(pre);
                }
            }
            // discover a special source loader to be used
            SourceLoader sl = getCamelContext().getRegistry().findSingleByType(SourceLoader.class);
            if (sl != null) {
                this.sourceLoader = sl;
            }
        }
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        final RouteBuilder builder = doLoadRouteBuilder(resource);
        if (builder != null) {
            CamelContextAware.trySetCamelContext(builder, getCamelContext());
            builder.setResource(resource);

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
        }

        return builder;
    }

    /**
     * Gets the input stream to the resource
     *
     * @param  resource the resource
     * @return          the input stream
     */
    protected InputStream resourceInputStream(Resource resource) throws IOException {
        // load into memory as we need to skip a specific first-line if present
        String data = sourceLoader.loadResource(resource);
        if (data.isBlank()) {
            throw new IOException("Resource is empty: " + resource.getLocation());
        }
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Loads {@link RoutesBuilder} from {@link Resource} from the DSL implementation.
     *
     * @param  resource the resource to be loaded.
     * @return          a {@link RoutesBuilder}
     */
    protected abstract RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception;

}
