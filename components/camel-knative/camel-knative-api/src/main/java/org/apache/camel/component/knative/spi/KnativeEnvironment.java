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
package org.apache.camel.component.knative.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.Configurer;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ResourceHelper;

/*
 * Assuming it is loaded from a json for now
 */
@Configurer
public class KnativeEnvironment {
    private final List<KnativeResource> resources;

    public KnativeEnvironment() {
        this.resources = new ArrayList<>();
    }

    public KnativeEnvironment(Collection<KnativeResource> resources) {
        this.resources = new ArrayList<>(resources);
    }

    @JsonAlias("services")
    @JsonProperty(value = "resources", required = true)
    public List<KnativeResource> getResources() {
        return resources;
    }

    @JsonAlias("services")
    @JsonProperty(value = "resources", required = true)
    public void setResources(List<KnativeResource> resources) {
        this.resources.clear();
        this.resources.addAll(resources);
    }

    public Stream<KnativeResource> stream() {
        return resources.stream();
    }

    public Stream<KnativeResource> lookup(Knative.Type type, String name) {
        return stream().filter(definition -> definition.matches(type, name));
    }

    // ************************
    //
    // Helpers
    //
    // ************************

    /**
     * Construct an instance o a {@link KnativeEnvironment} from a json serialized string.
     *
     * <pre>
     * {@code
     * {
     *     "resources": [
     *         {
     *              "type": "channel|endpoint|event",
     *              "name": "",
     *              "url": "",
     *              "path": "",
     *              "eventType": "",
     *              "objectKind": "",
     *              "objectApiVersion": "",
     *              "endpointKind": "source|sink",
     *              "filters": {
     *                  "header": "value"
     *              },
     *              "ceOverrides": {
     *                  "ce-type": "something"
     *              }
     *         },
     *     ]
     * }
     * }
     * </pre>
     *
     * @param  configuration the serialized representation of the Knative environment
     * @return               an instance of {@link KnativeEnvironment}
     * @throws IOException   if an error occur while parsing the file
     */
    public static KnativeEnvironment mandatoryLoadFromSerializedString(String configuration) throws IOException {
        try (Reader reader = new StringReader(configuration)) {
            return Knative.MAPPER.readValue(reader, KnativeEnvironment.class);
        }
    }

    /**
     * Construct an instance o a {@link KnativeEnvironment} from a properties.
     *
     * <pre>
     * {@code
     * resources[0].name = ...
     * resources[0].type = channel|endpoint|event
     * resources[0].endpointKind = source|sink
     * resources[0].url = ...
     * }
     * </pre>
     *
     * @param  context     the {@link CamelContext}
     * @param  properties  the properties from which to construct the {@link KnativeEnvironment}
     * @return             an instance of {@link KnativeEnvironment}
     * @throws IOException if an error occur while parsing the file
     */
    public static KnativeEnvironment mandatoryLoadFromProperties(CamelContext context, Map<String, Object> properties) {
        final ExtendedCamelContext econtext = context.getCamelContextExtension();
        final KnativeEnvironment environment = new KnativeEnvironment();

        PropertyBindingSupport.build()
                .withIgnoreCase(true)
                .withCamelContext(context)
                .withTarget(environment)
                .withProperties(properties)
                .withRemoveParameters(true)
                .withConfigurer(
                        PluginHelper.getConfigurerResolver(econtext)
                                .resolvePropertyConfigurer(KnativeEnvironment.class.getName(), context))
                .withMandatory(true)
                .bind();

        return environment;
    }

    /**
     * Construct an instance o a {@link KnativeEnvironment} from a json file.
     *
     * <pre>
     * {@code
     * {
     *     "resources": [
     *         {
     *              "type": "channel|endpoint|event",
     *              "name": "",
     *              "url": "",
     *              "path": "",
     *              "eventType": "",
     *              "objectKind": "",
     *              "objectApiVersion": "",
     *              "endpointKind": "source|sink",
     *              "filters": {
     *                  "header": "value"
     *              },
     *              "ceOverrides": {
     *                  "ce-type": "something"
     *              }
     *         },
     *     ]
     * }
     * }
     * </pre>
     *
     * @param  context     the {@link CamelContext}
     * @param  path        URI of the resource
     * @return             an instance of {@link KnativeEnvironment}
     * @throws IOException if an error occur while parsing the file
     */
    public static KnativeEnvironment mandatoryLoadFromResource(CamelContext context, String path) throws IOException {
        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, path)) {
            return Knative.MAPPER.readValue(is, KnativeEnvironment.class);
        }
    }

    public static KnativeEnvironment on(KnativeResource... definitions) {
        KnativeEnvironment env = new KnativeEnvironment();
        for (KnativeResource definition : definitions) {
            env.getResources().add(definition);
        }

        return env;
    }

    public static KnativeServiceBuilder serviceBuilder(Knative.Type type, String name) {
        return new KnativeServiceBuilder(type, name);
    }

    // ************************
    //
    // Types
    //
    // ************************

    public static final class KnativeServiceBuilder {
        private final Knative.Type type;
        private final String name;
        private Knative.EndpointKind endpointKind;
        private String url;
        private Map<String, String> metadata;

        public KnativeServiceBuilder(Knative.Type type, String name) {
            this.type = type;
            this.name = name;
        }

        public KnativeServiceBuilder withUrl(String url) {
            this.url = url;
            return this;
        }

        public KnativeServiceBuilder withUrlf(String format, Object... args) {
            return withUrl(String.format(format, args));
        }

        public KnativeServiceBuilder withEndpointKind(Knative.EndpointKind endpointKind) {
            this.endpointKind = endpointKind;
            return this;
        }

        public KnativeServiceBuilder withMeta(Map<String, String> metadata) {
            if (metadata == null) {
                return this;
            }
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.putAll(metadata);
            return this;
        }

        public KnativeServiceBuilder withMeta(String key, String value) {
            if (key == null || value == null) {
                return this;
            }
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public KnativeServiceBuilder withMeta(String key, Enum<?> e) {
            if (key == null || e == null) {
                return this;
            }
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, e.name());
            return this;
        }

        public KnativeResource build() {
            KnativeResource answer = new KnativeResource();
            answer.setType(type);
            answer.setEndpointKind(endpointKind);
            answer.setName(name);
            answer.setUrl(url);
            answer.setMetadata(metadata);

            return answer;
        }
    }

}
