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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TraitsBuilder {
    private Map<String, Addons> addons;
    private CamelBuilder camel;
    private ContainerBuilder container;
    private EnvironmentBuilder environment;
    private IngressBuilder ingress;
    private KnativeBuilder knative;
    private KnativeServiceBuilder knativeService;
    private MountBuilder mount;
    private OpenapiBuilder openapi;
    private RouteBuilder route;
    private ServiceBuilder service;
    private ServiceBindingBuilder serviceBinding;

    private TraitsBuilder() {
    }

    public static TraitsBuilder traits() {
        return new TraitsBuilder();
    }

    public TraitsBuilder addToAddons(String key, Addons value) {
        if (this.addons == null && key != null && value != null) {
            this.addons = new LinkedHashMap<>();
        }

        if (key != null && value != null) {
            this.addons.put(key, value);
        }

        return this;
    }

    public TraitsBuilder addToAddons(Map<String, Addons> map) {
        if (this.addons == null && map != null) {
            this.addons = new LinkedHashMap<>();
        }

        if (map != null) {
            this.addons.putAll(map);
        }

        return this;
    }

    public TraitsBuilder removeFromAddons(String key) {
        if (this.addons != null) {
            if (key != null) {
                this.addons.remove(key);
            }

        }
        return this;
    }

    public TraitsBuilder removeFromAddons(Map<String, Addons> map) {
        if (this.addons != null) {
            if (map != null) {
                Iterator<String> it = map.keySet().iterator();

                while (it.hasNext()) {
                    Object key = it.next();
                    if (this.addons != null) {
                        this.addons.remove(key);
                    }
                }
            }

        }
        return this;
    }

    public Map<String, Addons> getAddons() {
        return this.addons;
    }

    public <K, V> TraitsBuilder withAddons(Map<String, Addons> addons) {
        if (addons == null) {
            this.addons = null;
        } else {
            this.addons = new LinkedHashMap<>(addons);
        }

        return this;
    }

    public boolean hasAddons() {
        return this.addons != null;
    }

    public TraitsBuilder withCamel(CamelBuilder camel) {
        this.camel = camel;
        return this;
    }

    public TraitsBuilder withContainer(ContainerBuilder container) {
        this.container = container;
        return this;
    }

    public TraitsBuilder withEnvironment(EnvironmentBuilder environment) {
        this.environment = environment;
        return this;
    }

    public TraitsBuilder withIngress(IngressBuilder ingress) {
        this.ingress = ingress;
        return this;
    }

    public TraitsBuilder withKnative(KnativeBuilder knative) {
        this.knative = knative;
        return this;
    }

    public TraitsBuilder withKnativeService(KnativeServiceBuilder knativeService) {
        this.knativeService = knativeService;
        return this;
    }

    public TraitsBuilder withMount(MountBuilder mount) {
        this.mount = mount;
        return this;
    }

    public TraitsBuilder withOpenapi(OpenapiBuilder openapi) {
        this.openapi = openapi;
        return this;
    }

    public TraitsBuilder withRoute(RouteBuilder route) {
        this.route = route;
        return this;
    }

    public TraitsBuilder withService(ServiceBuilder service) {
        this.service = service;
        return this;
    }

    public TraitsBuilder withServiceBinding(ServiceBindingBuilder serviceBinding) {
        this.serviceBinding = serviceBinding;
        return this;
    }

    public Traits build() {
        Traits traits = new Traits();
        traits.setAddons(addons);
        if (camel != null) {
            traits.setCamel(camel.build());
        }
        if (container != null) {
            traits.setContainer(container.build());
        }
        if (environment != null) {
            traits.setEnvironment(environment.build());
        }
        if (ingress != null) {
            traits.setIngress(ingress.build());
        }
        if (knative != null) {
            traits.setKnative(knative.build());
        }
        if (knativeService != null) {
            traits.setKnativeService(knativeService.build());
        }
        if (mount != null) {
            traits.setMount(mount.build());
        }
        if (openapi != null) {
            traits.setOpenapi(openapi.build());
        }
        if (route != null) {
            traits.setRoute(route.build());
        }
        if (service != null) {
            traits.setService(service.build());
        }
        if (serviceBinding != null) {
            traits.setServiceBinding(serviceBinding.build());
        }
        return traits;
    }
}
