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

import java.util.Set;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.guice.inject.Injectors;

/**
 * A Guice Module which injects the CamelContext with all available implementations
 * of {@link org.apache.camel.RoutesBuilder} which are bound to Guice with an optional {@link Matcher} to filter out the classes required.
 * <p>
 * Or if you would like to specify exactly which {@link org.apache.camel.RoutesBuilder} to bind then use the {@link CamelModule} and create a provider
 * method annotated with @Provides and returning Set<Routes> such as
 * <code><pre>
 * public class MyModule extends CamelModule {
 *   &#64;Provides
 *   Set&lt;Routes&gt; routes(Injector injector) { ... }
 * }
 * </pre></code>
 *
 * @version 
 */
@SuppressWarnings("rawtypes")
public class CamelModuleWithMatchingRoutes extends CamelModule {
    private final Matcher<Class> matcher;

    public CamelModuleWithMatchingRoutes() {
        this(Matchers.subclassesOf(RoutesBuilder.class));
    }

    public CamelModuleWithMatchingRoutes(Matcher<Class> matcher) {
        this.matcher = matcher;
    }

    @Provides
    Set<RoutesBuilder> routes(Injector injector) {
        return Injectors.getInstancesOf(injector, matcher);
    }
}