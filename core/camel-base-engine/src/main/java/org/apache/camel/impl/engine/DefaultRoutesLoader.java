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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

public class DefaultRoutesLoader implements RoutesLoader {
    private CamelContext camelContext;

    public DefaultRoutesLoader() {
    }

    public DefaultRoutesLoader(CamelContext camelContext) {
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
    public Collection<RoutesBuilder> findRoutesBuilders(Collection<Resource> resources) throws Exception {
        List<RoutesBuilder> answer = new ArrayList<>(resources.size());

        for (Resource resource : resources) {
            // the loader to use is derived from the file extension
            final String extension = FileUtil.onlyExt(resource.getLocation(), true);

            if (ObjectHelper.isEmpty(extension)) {
                throw new IllegalArgumentException(
                        "Unable to determine file extension for resource: " + resource.getLocation());
            }

            answer.add(getRoutesLoader(extension).loadRoutesBuilder(resource));
        }

        return answer;
    }

    /**
     * Looks up a {@link RoutesBuilderLoader} in the registry or fallback to a factory finder mechanism if none found.
     *
     * @param  extension                the file extension for which a loader should be find.
     * @return                          a {@link RoutesBuilderLoader}
     * @throws IllegalArgumentException if no {@link RoutesBuilderLoader} can be found for the given file extension
     */
    private RoutesBuilderLoader getRoutesLoader(String extension) {
        RoutesBuilderLoader answer = getCamelContext().getRegistry().lookupByNameAndType(extension, RoutesBuilderLoader.class);

        if (answer == null) {
            final ExtendedCamelContext ecc = getCamelContext().adapt(ExtendedCamelContext.class);
            final FactoryFinder finder = ecc.getBootstrapFactoryFinder(RoutesBuilderLoader.FACTORY_PATH);

            answer = ResolverHelper.resolveService(ecc, finder, extension, RoutesBuilderLoader.class).orElse(null);
        }
        if (answer == null) {
            throw new IllegalArgumentException(
                    "Unable to fina a RoutesBuilderLoader for resource with file extension: " + extension);
        }

        return CamelContextAware.trySetCamelContext(answer, getCamelContext());
    }
}
