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

import java.util.Set;

import org.apache.camel.CamelContext;

/**
 * Discovers the names of all {@link org.apache.camel.Component} implementations available on the classpath at runtime.
 * <p/>
 * The default implementation reads {@code META-INF/services/org/apache/camel/component/} resource entries to enumerate
 * registered component names without instantiating them. This list is used by Camel tooling features such as
 * auto-completion in the <a href="https://camel.apache.org/manual/camel-jbang.html">Camel JBang</a> CLI and the catalog
 * to answer questions like "which components are installed?" without triggering lazy component loading.
 *
 * @see   ComponentResolver
 * @since 3.2
 */
public interface ComponentNameResolver {

    /**
     * Discovers which components are available on the classpath.
     *
     * @param  camelContext the camel context
     * @return              the component names on the classpath
     */
    Set<String> resolveNames(CamelContext camelContext);
}
