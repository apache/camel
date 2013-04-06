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

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * Create a collection of routes via a provider method
 *
 * @version 
 */
public class CollectionOfCustomRoutesTest extends Assert {

    public static class MyModule extends CamelModuleWithMatchingRoutes {
        @Provides
        @Named("foo")
        protected Collection<? extends RoutesBuilder> myRoutes() {
            return Lists.newArrayList(new MyConfigurableRoute2("direct:a", "direct:b"), new MyConfigurableRoute2("direct:c", "direct:d"));
        }
    }

    @Test
    public void testDummy() throws Exception {
    }

    public void xtestGuice() throws Exception {
        Injector injector = Guice.createInjector(new MyModule());

        CamelContext camelContext = injector.getInstance(CamelContext.class);
        List<Route> list = camelContext.getRoutes();
        assertEquals("size of " + list, 2, list.size());

        GuiceTest.assertCamelContextRunningThenCloseInjector(injector);
    }

}