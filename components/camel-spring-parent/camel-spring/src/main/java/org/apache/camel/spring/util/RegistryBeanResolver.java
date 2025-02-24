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
package org.apache.camel.spring.util;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.Registry;
import org.springframework.expression.AccessException;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;

/**
 * EL bean resolver that operates against a Camel {@link Registry}.
 */
public final class RegistryBeanResolver implements BeanResolver {

    private final Registry registry;

    public RegistryBeanResolver(Registry registry) {
        this.registry = registry;
    }

    @Override
    public Object resolve(EvaluationContext context, String beanName) throws AccessException {
        Object bean = null;
        try {
            bean = registry.lookupByName(beanName);
        } catch (NoSuchBeanException e) {
            // ignore
        }
        if (bean == null) {
            throw new AccessException("Could not resolve bean reference against Registry");
        }
        return bean;
    }

}
