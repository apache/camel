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
package org.apache.camel.impl.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default language resolver that looks for language factories in <b>META-INF/services/org/apache/camel/language/</b>
 * and language resolvers in <b>META-INF/services/org/apache/camel/language/resolver/</b>.
 */
public class DefaultLanguageResolver implements LanguageResolver {
    public static final String LANGUAGE_RESOURCE_PATH = "META-INF/services/org/apache/camel/language/";
    public static final String LANGUAGE_RESOLVER_RESOURCE_PATH = LANGUAGE_RESOURCE_PATH + "resolver/";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLanguageResolver.class);

    protected FactoryFinder languageFactory;
    protected FactoryFinder languageResolver;

    @Override
    public Language resolveLanguage(String name, CamelContext context) {
        Class<?> type = null;
        try {
            type = findLanguage(name, context);
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no Language registered for scheme: " + name, e);
        }

        if (type != null) {
            if (Language.class.isAssignableFrom(type)) {
                return (Language) context.getInjector().newInstance(type, false);
            } else {
                throw new IllegalArgumentException(
                        "Resolving language: " + name + " detected type conflict: Not a Language implementation. Found: "
                                                   + type.getName());
            }
        } else {
            // no specific language found then try fallback
            return noSpecificLanguageFound(name, context);
        }
    }

    protected Language noSpecificLanguageFound(String name, CamelContext context) {
        Class<?> type = null;
        try {
            type = findLanguageResolver("default", context);
        } catch (NoFactoryAvailableException | ClassNotFoundException e) {
            // ignore
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no LanguageResolver registered for scheme: " + name, e);
        }
        if (type != null) {
            if (LanguageResolver.class.isAssignableFrom(type)) {
                LanguageResolver resolver = (LanguageResolver) context.getInjector().newInstance(type, false);
                return resolver.resolveLanguage(name, context);
            } else {
                throw new IllegalArgumentException(
                        "Resolving language: " + name
                                                   + " detected type conflict: Not a LanguageResolver implementation. Found: "
                                                   + type.getName());
            }
        }
        throw new NoSuchLanguageException(name);
    }

    protected Class<?> findLanguage(String name, CamelContext context) throws Exception {
        if (languageFactory == null) {
            languageFactory = context.getCamelContextExtension().getFactoryFinder(LANGUAGE_RESOURCE_PATH);
        }
        return languageFactory.findClass(name).orElse(null);
    }

    protected Class<?> findLanguageResolver(String name, CamelContext context) throws Exception {
        if (languageResolver == null) {
            languageResolver = context.getCamelContextExtension().getFactoryFinder(LANGUAGE_RESOLVER_RESOURCE_PATH);
        }
        return languageResolver.findClass(name).orElse(null);
    }

    protected Logger getLog() {
        return LOG;
    }
}
