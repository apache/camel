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
package org.apache.camel.dsl.yaml;

import java.io.InputStream;
import java.util.List;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StartupStep;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.deserializers.CustomResolver;
import org.apache.camel.dsl.yaml.deserializers.EndpointProducerDeserializersResolver;
import org.apache.camel.dsl.yaml.deserializers.ModelDeserializersResolver;
import org.apache.camel.dsl.yaml.deserializers.model.OutputAwareFromDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.RoutesBuilderLoaderSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

@ManagedResource(description = "Managed YAML RoutesBuilderLoader")
@RoutesLoader(YamlRoutesBuilderLoader.EXTENSION)
public class YamlRoutesBuilderLoader extends RoutesBuilderLoaderSupport {
    public static final String EXTENSION = "yaml";

    private LoadSettings settings;
    private YamlDeserializationContext constructor;
    private StartupStepRecorder recorder;

    public YamlRoutesBuilderLoader() {
    }

    @ManagedAttribute(description = "Supported file extension")
    @Override
    public String getSupportedExtension() {
        return EXTENSION;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        this.recorder = getCamelContext().adapt(ExtendedCamelContext.class).getStartupStepRecorder();

        this.settings = LoadSettings.builder().build();
        this.constructor = new YamlDeserializationContext(settings);
        this.constructor.setCamelContext(getCamelContext());
        this.constructor.addResolvers(new CustomResolver());
        this.constructor.addResolvers(new ModelDeserializersResolver());
        this.constructor.addResolvers(new EndpointProducerDeserializersResolver());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ServiceHelper.startService(this.constructor);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(this.constructor);

        this.constructor = null;
        this.settings = null;
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        ObjectHelper.notNull(constructor, "constructor");
        ObjectHelper.notNull(settings, "settings");

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final Load load = new Load(settings, constructor);

                StartupStep step = recorder != null
                        ? recorder.beginStep(YamlRoutesBuilderLoader.class, resource.getLocation(),
                                "Loading and Parsing YAML routes")
                        : null;

                try (InputStream is = resource.getInputStream()) {
                    for (Object item : (List<?>) load.loadFromInputStream(is)) {

                        configure(item);
                    }
                } finally {
                    if (recorder != null) {
                        recorder.endStep(step);
                    }
                }
            }

            private void configure(Object item) {
                if (item instanceof OutputAwareFromDefinition) {
                    RouteDefinition route = new RouteDefinition();
                    route.setInput(((OutputAwareFromDefinition) item).getDelegate());
                    route.setOutputs(((OutputAwareFromDefinition) item).getOutputs());
                    getRouteCollection().route(route);
                } else if (item instanceof RouteDefinition) {
                    getRouteCollection().route((RouteDefinition) item);
                } else if (item instanceof RestDefinition) {
                    RestDefinition definition = (RestDefinition) item;
                    for (VerbDefinition verb : definition.getVerbs()) {
                        verb.setRest(definition);
                    }
                    getRestCollection().rest(definition);
                } else if (item instanceof CamelContextCustomizer) {
                    ((CamelContextCustomizer) item).configure(getCamelContext());
                } else if (item instanceof OnExceptionDefinition) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException("onException must be defined before any routes in the RouteBuilder");
                    }
                    getRouteCollection().getOnExceptions().add((OnExceptionDefinition) item);
                } else if (item instanceof ErrorHandlerBuilder) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "errorHandler must be defined before any routes in the RouteBuilder");
                    }
                    errorHandler((ErrorHandlerBuilder) item);
                }
            }
        };
    }

}
