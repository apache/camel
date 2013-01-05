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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.guice.inject.Injectors;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;

/**
 * Lets use a custom CamelModule to perform explicit binding of route builders
 * 
 * @version
 */
public class GuiceRouteWithNamedKeysTest extends TestSupport {

    public static class MyModule extends CamelModuleWithMatchingRoutes {

        @Provides
        @Named("foo")
        protected MyConfigurableRoute2 createRoute1() {
            return new MyConfigurableRoute2("direct:a", "direct:b");
        }
    }

    @Test
    public void testGuice() throws Exception {
        Injector injector = Guice.createInjector(new MyModule());

        MyConfigurableRoute2 instance = injector.getInstance(Key.get(MyConfigurableRoute2.class,
                                                                     Names.named("foo")));
        assertNotNull("should have found a key for 'foo'", instance);

        log.info("Found instance: " + instance);

        // List<Binding<RouteBuilder>> list =
        // injector.findBindingsByType(TypeLiteral.get(RouteBuilder.class));
        Collection<RouteBuilder> list = Injectors.getInstancesOf(injector, RouteBuilder.class);
        log.info("RouteBuilder List: " + list);

        assertEquals("route builder list: " + list, 1, list.size());

        list = Injectors.getInstancesOf(injector, Matchers.subclassesOf(RouteBuilder.class));
        log.info("RouteBuilder List: " + list);

        assertEquals("route builder list: " + list, 1, list.size());
        /*
         * list = Injectors.getInstancesOf(injector,
         * Matchers.subclassesOf(RouteBuilder
         * .class).and(Matchers.annotatedWith(Names.named("foo"))));
         * log.info("RouteBuilder List: " + list);
         * assertEquals("route builder list: " + list, 1, list.size()); list =
         * Injectors.getInstancesOf(injector, Matchers.subclassesOf(RouteBuilder
         * .class).and(Matchers.annotatedWith(Names.named("bar"))));
         * log.info("RouteBuilder List: " + list);
         * assertEquals("route builder list: " + list, 0, list.size());
         */

        GuiceTest.assertCamelContextRunningThenCloseInjector(injector);
    }

}