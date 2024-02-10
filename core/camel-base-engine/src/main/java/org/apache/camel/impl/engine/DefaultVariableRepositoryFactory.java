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

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.VariableRepository;
import org.apache.camel.spi.VariableRepositoryFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.GlobalVariableRepository;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.RouteVariableRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link VariableRepositoryFactory}.
 */
public class DefaultVariableRepositoryFactory extends ServiceSupport implements VariableRepositoryFactory, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultVariableRepositoryFactory.class);

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/variable-repository/";

    private final CamelContext camelContext;
    private VariableRepository global;
    private VariableRepository route;
    private FactoryFinder factoryFinder;

    public DefaultVariableRepositoryFactory(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public VariableRepository getVariableRepository(String id) {
        // ensure we are started if in use
        if (!isStarted()) {
            start();
        }

        if (global != null && "global".equals(id)) {
            return global;
        }
        if (route != null && "route".equals(id)) {
            return route;
        }

        VariableRepository repo = CamelContextHelper.lookup(camelContext, id, VariableRepository.class);
        if (repo == null) {
            repo = CamelContextHelper.lookup(camelContext, id + "-variable-repository", VariableRepository.class);
        }
        if (repo == null) {
            // try via factory finder
            Class<?> clazz = factoryFinder.findClass(id).orElse(null);
            if (clazz != null && VariableRepository.class.isAssignableFrom(clazz)) {
                repo = (VariableRepository) camelContext.getInjector().newInstance(clazz, true);
                camelContext.getRegistry().bind(id, repo);
                try {
                    camelContext.addService(repo);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            }
        }

        return repo;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        this.factoryFinder = camelContext.getCamelContextExtension().getBootstrapFactoryFinder(RESOURCE_PATH);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // let's see if there is a custom global repo
        VariableRepository repo = getVariableRepository("global");
        if (repo != null) {
            if (!(repo instanceof GlobalVariableRepository)) {
                LOG.info("Using VariableRepository: {} as global repository", repo.getId());
            }
            global = repo;
        } else {
            global = new GlobalVariableRepository();
            camelContext.getRegistry().bind(GLOBAL_VARIABLE_REPOSITORY_ID, global);
        }
        // let's see if there is a custom route repo
        repo = getVariableRepository("route");
        if (repo != null) {
            if (!(repo instanceof RouteVariableRepository)) {
                LOG.info("Using VariableRepository: {} as route repository", repo.getId());
            }
            route = repo;
        } else {
            route = new RouteVariableRepository();
            camelContext.getRegistry().bind(ROUTE_VARIABLE_REPOSITORY_ID, route);
        }

        if (!camelContext.hasService(global)) {
            camelContext.addService(global);
        }
        if (!camelContext.hasService(route)) {
            camelContext.addService(route);
            camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
                @Override
                public void onRoutesRemove(Collection<Route> routes) {
                    // remove all variables from this route
                    for (Route r : routes) {
                        route.removeVariable(r.getRouteId() + ":*");
                    }
                }
            });
        }
    }

}
