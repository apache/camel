/**
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
package org.apache.camel.spring.cloud;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.impl.cloud.AbstractServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.core.convert.ConversionService;

public class CamelSpringCloudServiceRegistry extends AbstractServiceRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelSpringCloudServiceRegistry.class);

    private final List<ServiceDefinition> definitions;
    private final List<ConversionService> conversionServices;
    private final ServiceRegistry serviceRegistry;
    private final Class<? extends Registration> registrationType;

    public CamelSpringCloudServiceRegistry(Collection<ConversionService> conversionServices, ServiceRegistry serviceRegistry) {
        this.definitions = new ArrayList<>();
        this.conversionServices = new ArrayList<>(conversionServices);
        this.serviceRegistry = serviceRegistry;
        this.registrationType = determineRegistrationType("register");
    }

    @Override
    public void register(ServiceDefinition definition) {
        synchronized (this) {
            // keep track of registered definition to remove them upon registry
            // shutdown
            if (definitions.stream().noneMatch(d -> matchById(d, definition))) {
                LOGGER.debug("Register service with definition: {} with registrations: {}", definition, registrationType);

                // compute registration from definition
                Registration result = convertServiceDefinition(definition);

                serviceRegistry.register(result);

                definitions.add(definition);
            }
        }
    }

    @Override
    public void deregister(ServiceDefinition definition) {
        synchronized (this) {
            if (definitions.stream().noneMatch(d -> matchById(d, definition))) {
                LOGGER.debug("Deregister service with definition: {} with registrations: {}", definition, registrationType);
                
                // compute registration from definition
                Registration result = convertServiceDefinition(definition);

                serviceRegistry.deregister(result);
            }

            // remove any instance with the same id
            definitions.removeIf(d -> matchById(d, definition));
        }
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
        synchronized (this) {
            new ArrayList<>(definitions).forEach(this::deregister);
        }
    }

    public ServiceRegistry getNativeServiceRegistry() {
        return this.serviceRegistry;
    }

    public <R extends Registration, T extends ServiceRegistry<R>> T getNativeServiceRegistry(Class<T> type) {
        return type.cast(this.serviceRegistry);
    }

    /**
     * Determine the native registration type. This is needed because the registry
     * specific implementation provided by spring-cloud-xyz does not handle generic
     * Registration object but needs a Registration specific to the underlying
     * technology used.
     *
     * @return the registration type
     */
    private Class<? extends Registration> determineRegistrationType(String methodName) {
        Class<? extends Registration> type = null;
        Method[] methods = serviceRegistry.getClass().getDeclaredMethods();

        for (Method method: methods) {
            if (!methodName.equals(method.getName())) {
                continue;
            }

            if (method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (Registration.class.isAssignableFrom(parameterType)) {
                if (type == null) {
                    type = (Class<? extends Registration>)parameterType;
                } else {
                    if (type.isAssignableFrom(parameterType)) {
                        type = (Class<? extends Registration>)parameterType;
                    }
                }
            }
        }

        return type != null ? type : Registration.class;
    }

    private boolean matchById(ServiceDefinition definition, ServiceDefinition reference) {
        if (definition.getId() == null || reference.getId() == null) {
            return false;
        }

        return Objects.equals(definition.getId(), reference.getId());
    }

    private Registration convertServiceDefinition(ServiceDefinition definition) {
        for (int i = 0; i < conversionServices.size(); i++) {
            ConversionService cs = conversionServices.get(i);

            if (cs.canConvert(ServiceDefinition.class, registrationType)) {
                return cs.convert(definition, registrationType);
            }
        }

        throw new IllegalStateException("Unable to convert service definition to native registration of type:" + registrationType);
    }
}
