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
package org.apache.camel.main.download;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.BootstrapFactoryFinder;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;

public class DependencyDownloadFactoryFinderResolver implements FactoryFinderResolver {

    private final CamelContext camelContext;
    private final KnownDependenciesResolver knownDeps;

    public DependencyDownloadFactoryFinderResolver(CamelContext camelContext, KnownDependenciesResolver knownDeps) {
        this.camelContext = camelContext;
        this.knownDeps = knownDeps;
    }

    @Override
    public FactoryFinder resolveBootstrapFactoryFinder(ClassResolver classResolver, String resourcePath) {
        return new BootstrapFactoryFinder(classResolver, resourcePath);
    }

    @Override
    public FactoryFinder resolveFactoryFinder(ClassResolver classResolver, String resourcePath) {
        return new DependencyDownloadFactoryFinder(camelContext, classResolver, resourcePath, knownDeps);
    }
}
