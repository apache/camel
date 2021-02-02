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

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

public class DefaultRoutesBuilderLoader implements RoutesBuilderLoader, CamelContextAware {
    private CamelContext camelContext;

    public DefaultRoutesBuilderLoader() {
    }

    public DefaultRoutesBuilderLoader(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        // language is derived from the file extension
        final String language = FileUtil.onlyExt(resource.getLocation(), true);

        if (ObjectHelper.isEmpty(language)) {
            throw new IllegalArgumentException("Unable to determine language for resource: " + resource.getLocation());
        }

        return getLoader(language).loadRoutesBuilder(resource);
    }

    private RoutesBuilderLoader getLoader(String language) {
        RoutesBuilderLoader answer = getCamelContext().getRegistry().lookupByNameAndType(language, RoutesBuilderLoader.class);

        if (answer == null) {
            final ExtendedCamelContext ecc = getCamelContext(ExtendedCamelContext.class);
            final FactoryFinderResolver ffr = ecc.getFactoryFinderResolver();
            final ClassResolver cr = ecc.getClassResolver();
            final FactoryFinder finder = ffr.resolveBootstrapFactoryFinder(cr, RoutesBuilderLoader.LOADERS_FACTORY_PATH);

            final BaseServiceResolver<RoutesBuilderLoader> resolver
                    = new BaseServiceResolver<>(language, RoutesBuilderLoader.class, finder);
            final Optional<RoutesBuilderLoader> loader
                    = resolver.resolve(ecc);

            if (loader.isPresent()) {
                return CamelContextAware.trySetCamelContext(loader.get(), ecc);
            } else {
                throw new IllegalArgumentException("Unable to fina a RoutesBuilderLoader for language " + language);
            }
        }

        return answer;
    }
}
