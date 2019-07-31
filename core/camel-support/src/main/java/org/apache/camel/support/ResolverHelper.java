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
import org.apache.camel.Component;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatFactory;
import org.apache.camel.spi.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some helper methods for new resolvers (like {@link org.apache.camel.spi.ComponentResolver}, {@link org.apache.camel.spi.DataFormatResolver}, etc.).
 */
public final class ResolverHelper {

    public static final String COMPONENT_FALLBACK_SUFFIX = "-component";

    public static final String DATA_FORMAT_FALLBACK_SUFFIX = "-dataformat";

    public static final String DATA_FORMAT_FACTORY_FALLBACK_SUFFIX = "-dataformat-factory";

    public static final String LANGUAGE_FALLBACK_SUFFIX = "-language";

    private static final Logger LOG = LoggerFactory.getLogger(ResolverHelper.class);

    private static final LookupExceptionHandler EXCEPTION_HANDLER = new LookupExceptionHandler();

    /**
     * Utility classes should not have a public constructor.
     */
    private ResolverHelper() {
    }

    public static Component lookupComponentInRegistryWithFallback(CamelContext context, String name) {
        return lookupComponentInRegistryWithFallback(context, name, EXCEPTION_HANDLER);
    }

    public static Component lookupComponentInRegistryWithFallback(CamelContext context, String name, LookupExceptionHandler exceptionHandler) {
        Object bean = lookupInRegistry(context, Component.class, false, exceptionHandler, name, name + COMPONENT_FALLBACK_SUFFIX);
        if (bean != null) {
            if (bean instanceof Component) {
                return (Component) bean;
            } else {
                // let's use Camel's type conversion mechanism to convert things like CamelContext
                // and other types into a valid Component
                Component component = CamelContextHelper.convertTo(context, Component.class, bean);
                if (component != null) {
                    return component;
                }
            }
        }

        if (bean != null) {
            LOG.debug("Found Component with incompatible class: {}", bean.getClass().getName());
        }
        return null;
    }

    public static DataFormat lookupDataFormatInRegistryWithFallback(CamelContext context, String name) {
        return lookupDataFormatInRegistryWithFallback(context, name, EXCEPTION_HANDLER);
    }

    public static DataFormat lookupDataFormatInRegistryWithFallback(CamelContext context, String name, LookupExceptionHandler exceptionHandler) {
        Object bean = lookupInRegistry(context, DataFormat.class, false, exceptionHandler, name, name + DATA_FORMAT_FALLBACK_SUFFIX);
        if (bean instanceof DataFormat) {
            return (DataFormat) bean;
        }

        if (bean != null) {
            LOG.debug("Found DataFormat with incompatible class: {}", bean.getClass().getName());
        }
        return null;
    }

    public static DataFormatFactory lookupDataFormatFactoryInRegistryWithFallback(CamelContext context, String name) {
        return lookupDataFormatFactoryInRegistryWithFallback(context, name, EXCEPTION_HANDLER);
    }

    public static DataFormatFactory lookupDataFormatFactoryInRegistryWithFallback(CamelContext context, String name, LookupExceptionHandler exceptionHandler) {
        Object bean = lookupInRegistry(context, DataFormatFactory.class, false, exceptionHandler, name, name + DATA_FORMAT_FACTORY_FALLBACK_SUFFIX);
        if (bean instanceof DataFormatFactory) {
            return (DataFormatFactory) bean;
        }

        if (bean != null) {
            LOG.debug("Found DataFormatFactory with incompatible class: {}", bean.getClass().getName());
        }
        return null;
    }

    public static Language lookupLanguageInRegistryWithFallback(CamelContext context, String name) {
        return lookupLanguageInRegistryWithFallback(context, name, EXCEPTION_HANDLER);
    }

    public static Language lookupLanguageInRegistryWithFallback(CamelContext context, String name, LookupExceptionHandler exceptionHandler) {
        Object bean = lookupInRegistry(context, Language.class, false, exceptionHandler, name, name + LANGUAGE_FALLBACK_SUFFIX);
        if (bean instanceof Language) {
            return (Language) bean;
        }

        if (bean != null) {
            LOG.debug("Found Language with incompatible class: {}", bean.getClass().getName());
        }
        return null;
    }


    public static class LookupExceptionHandler {

        public void handleException(Exception e, Logger log, String name) {
            log.debug("Ignored error looking up bean: {}", name, e);
        }

    }

    private static Object lookupInRegistry(CamelContext context, Class<?> type, boolean lookupByNameAndType, LookupExceptionHandler exceptionHandler, String... names) {
        for (String name : names) {
            try {
                Object bean;
                if (lookupByNameAndType) {
                    bean = context.getRegistry().lookupByNameAndType(name, type);
                } else {
                    bean = context.getRegistry().lookupByName(name);
                }
                LOG.debug("Lookup {} with name {} in registry. Found: {}", type.getSimpleName(), name, bean);
                if (bean != null) {
                    return bean;
                }
            } catch (Exception e) {
                exceptionHandler.handleException(e, LOG, name);
            }
        }

        return null;
    }

}
