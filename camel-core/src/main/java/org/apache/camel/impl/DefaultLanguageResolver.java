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
package org.apache.camel.impl;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.util.FactoryFinder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default language resolver that looks for language factories in <b>META-INF/services/org/apache/camel/language/</b> and
 * language resolvers in <b>META-INF/services/org/apache/camel/language/resolver/</b>.
 *
 * @version $Revision$
 */
public class DefaultLanguageResolver implements LanguageResolver {    
    protected static final FactoryFinder LANGUAGE_FACTORY = new FactoryFinder("META-INF/services/org/apache/camel/language/");
    protected static final FactoryFinder LANGUAGE_RESOLVER = new FactoryFinder("META-INF/services/org/apache/camel/language/resolver/");
    private static final transient Log LOG = LogFactory.getLog(DefaultLanguageResolver.class);
    
    @SuppressWarnings("unchecked")
    public Language resolveLanguage(String name, CamelContext context) {
        Object bean = null;
        try {
            bean = context.getRegistry().lookup(name);
            if (bean != null && LOG.isDebugEnabled()) {
                LOG.debug("Found language: " + name + " in registry: " + bean);
            }
        } catch (Exception e) {
            LOG.debug("Ignored error looking up bean: " + name + ". Error: " + e);
        }
        if (bean != null) {
            if (bean instanceof Language) {
                return (Language)bean;
            }
            // we do not throw the exception here and try to auto create a Language from META-INF
        }
        Class type = null;
        try {
            type = findLanguage(name);
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no Language registered for scheme: " + name, e);
        }
        if (type != null) {
            if (Language.class.isAssignableFrom(type)) {
                return (Language)context.getInjector().newInstance(type);
            } else {
                throw new IllegalArgumentException("Type is not a Language implementation. Found: " + type.getName());
            }
        }
        return noSpecificLanguageFound(name, context);
    }

    @SuppressWarnings("unchecked")
    protected Language noSpecificLanguageFound(String name, CamelContext context) {
        Class type = null;
        try {
            type = findLanguageResolver("default");
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no Language registered for scheme: " + name, e);
        }
        if (type != null) {
            if (LanguageResolver.class.isAssignableFrom(type)) {
                LanguageResolver resolver = (LanguageResolver)context.getInjector().newInstance(type);
                return resolver.resolveLanguage(name, context);
            } else {
                throw new IllegalArgumentException("Type is not a LanguageResolver implementation. Found: " + type.getName());
            }
        }
        throw new NoSuchLanguageException(name);
    }
    
    protected Class findLanguage(String name) throws Exception {
        return LANGUAGE_FACTORY.findClass(name);
    }
    
    protected Class findLanguageResolver(String name) throws Exception {
        return LANGUAGE_RESOLVER.findClass("default");
    }
}
