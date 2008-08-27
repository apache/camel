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
package org.apache.camel.guice;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

import org.apache.camel.CamelContext;
import org.apache.camel.Routes;
import org.apache.camel.builder.RouteBuilder;

/**
 * A default Guice module for creating a {@link CamelContext} and registering a list of {@link RouteBuilder} types to register.
 * <p/>
 * You can drive from this class to overload the {@link #configureRoutes(com.google.inject.multibindings.Multibinder)} method to perform custom binding for
 * route builders. Another approach is to create a {@link RouteBuilder} which just initialises all of your individual route builders
 *
 * @version $Revision$
 */
public class CamelModule extends AbstractModule {
    private List<Class<? extends RouteBuilder>> routeClassList;

    protected CamelModule(Class<? extends RouteBuilder>... routeTypes) {
        routeClassList = new ArrayList<Class<? extends RouteBuilder>>();
        for (Class<? extends RouteBuilder> routeType : routeTypes) {
            routeClassList.add(routeType);
        }
    }

    protected CamelModule(List<Class<? extends RouteBuilder>> routeClassList) {
        this.routeClassList = routeClassList;
    }

    protected void configure() {
        bind(CamelContext.class).to(GuiceCamelContext.class).asEagerSingleton();

        Multibinder<Routes> routesBinder = Multibinder.newSetBinder(binder(), Routes.class);

        for (Class<? extends Routes> routeType : routeClassList) {
            routesBinder.addBinding().to(routeType);
        }

        configureRoutes(routesBinder);
    }

    /**
     * Provides a strategy method configure the routes, typically via {@link RouteBuilder} instances
     *
     * @param routesBinder
     */
    protected void configureRoutes(Multibinder<Routes> routesBinder) {
    }

}
