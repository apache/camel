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

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.NoFactoryAvailableException;

/**
 * Default language resolver that looks for language factories in <b>META-INF/services/org/apache/camel/language/</b> and
 * language resolvers in <b>META-INF/services/org/apache/camel/language/resolver/</b>.
 *
 * @version $Revision$
 */
public class DefaultLanguageResolver implements LanguageResolver {
    protected static final FactoryFinder LANGUAGE_FACTORY = new FactoryFinder("META-INF/services/org/apache/camel/language/");
    protected static final FactoryFinder LANGUAGE_RESOLVER = new FactoryFinder("META-INF/services/org/apache/camel/language/resolver/");

    public Language resolveLanguage(String name, CamelContext context) {
        Class type = null;
        try {
            type = LANGUAGE_FACTORY.findClass(name);
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Throwable e) {
            throw new IllegalArgumentException("Invalid URI, no Language registered for scheme : " + name, e);
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

    protected Language noSpecificLanguageFound(String name, CamelContext context) {
        Class type = null;
        try {
            type = LANGUAGE_RESOLVER.findClass("default");
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Throwable e) {
            throw new IllegalArgumentException("Invalid URI, no Language registered for scheme : " + name, e);
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
}
