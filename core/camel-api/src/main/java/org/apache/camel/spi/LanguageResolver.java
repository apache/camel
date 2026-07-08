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
package org.apache.camel.spi;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchLanguageException;

/**
 * SPI strategy for resolving a {@link Language} implementation by name in a loosely coupled way.
 * <p/>
 * The default implementation first checks the Camel {@link Registry} for a bean registered under the language name,
 * then falls back to the classpath service files under {@code META-INF/services/org/apache/camel/language/}. Custom
 * resolvers can alias language names, redirect to a different implementation, or integrate with a non-classpath
 * language registry. Multiple resolvers can be registered on {@link org.apache.camel.CamelContext}; they are tried in
 * priority order until one returns a non-null result or {@link org.apache.camel.NoSuchLanguageException} is thrown.
 * <p/>
 * See <a href="https://camel.apache.org/manual/languages.html">Languages</a> in the Camel user manual.
 *
 * @see Language
 */
public interface LanguageResolver {

    /**
     * Resolves the given language.
     *
     * @param  name                    the name of the language
     * @param  context                 the camel context
     * @return                         the resolved language
     * @throws NoSuchLanguageException is thrown if language could not be resolved
     */
    Language resolveLanguage(String name, CamelContext context) throws NoSuchLanguageException;
}
