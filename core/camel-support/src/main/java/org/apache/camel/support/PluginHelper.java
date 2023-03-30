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

package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelDependencyInjectionAnnotationFactory;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.LanguageResolver;

public final class PluginHelper {
    private PluginHelper() {

    }

    /**
     * Returns the bean post processor used to do any bean customization.
     *
     * @return the bean post processor.
     */
    public static CamelBeanPostProcessor getBeanPostProcessor(CamelContext camelContext) {
        return getBeanPostProcessor(camelContext.getCamelContextExtension());
    }

    /**
     * Returns the bean post processor used to do any bean customization.
     *
     * @return the bean post processor.
     */
    public static CamelBeanPostProcessor getBeanPostProcessor(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(CamelBeanPostProcessor.class);
    }

    /**
     * Returns the annotation dependency injection factory.
     */
    public static CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory(CamelContext camelContext) {
        return getDependencyInjectionAnnotationFactory(camelContext.getCamelContextExtension());

    }

    /**
     * Returns the annotation dependency injection factory.
     */
    public static CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory(
            ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(CamelDependencyInjectionAnnotationFactory.class);
    }

    /**
     * Gets the {@link ComponentResolver} to use.
     */
    public static ComponentResolver getComponentResolver(CamelContext camelContext) {
        return getComponentResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ComponentResolver} to use.
     */
    public static ComponentResolver getComponentResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ComponentResolver.class);
    }

    /**
     * Gets the {@link ComponentNameResolver} to use.
     */
    public static ComponentNameResolver getComponentNameResolver(CamelContext camelContext) {
        return getComponentNameResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ComponentNameResolver} to use.
     */
    public static ComponentNameResolver getComponentNameResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ComponentNameResolver.class);
    }

    /**
     * Gets the {@link LanguageResolver} to use.
     */
    public static LanguageResolver getLanguageResolver(CamelContext camelContext) {
        return getLanguageResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link LanguageResolver} to use.
     */
    public static LanguageResolver getLanguageResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(LanguageResolver.class);
    }
}
