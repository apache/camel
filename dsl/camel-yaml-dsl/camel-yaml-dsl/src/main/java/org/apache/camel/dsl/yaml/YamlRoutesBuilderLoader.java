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
import java.util.Locale;
import java.util.Map;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializationMode;
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
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

@ManagedResource(description = "Managed YAML RoutesBuilderLoader")
@RoutesLoader(YamlRoutesBuilderLoader.EXTENSION)
public class YamlRoutesBuilderLoader extends RouteBuilderLoaderSupport {
    public static final String DESERIALIZATION_MODE = "CamelYamlDslDeserializationMode";
    public static final String EXTENSION = "yaml";

    private LoadSettings settings;
    private YamlDeserializationContext deserializationContext;

    public YamlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        this.settings = LoadSettings.builder().build();
        this.deserializationContext = new YamlDeserializationContext(settings);
        this.deserializationContext.setCamelContext(getCamelContext());
        this.deserializationContext.addResolvers(new CustomResolver());
        this.deserializationContext.addResolvers(new ModelDeserializersResolver());
        this.deserializationContext.addResolvers(new EndpointProducerDeserializersResolver());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final Map<String, String> options = getCamelContext().getGlobalOptions();
        final String mode = options.getOrDefault(DESERIALIZATION_MODE, YamlDeserializationMode.CLASSIC.name());
        if (mode != null) {
            this.deserializationContext.setDeserializationMode(
                    YamlDeserializationMode.valueOf(mode.toUpperCase(Locale.US)));
        }

        ServiceHelper.startService(this.deserializationContext);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(this.deserializationContext);

        this.deserializationContext = null;
        this.settings = null;
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        ObjectHelper.notNull(deserializationContext, "constructor");
        ObjectHelper.notNull(settings, "settings");

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final Load load = new Load(settings, deserializationContext);

                try (InputStream is = resource.getInputStream()) {
                    for (Object item : (List<?>) load.loadFromInputStream(is)) {

                        configure(item);
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
