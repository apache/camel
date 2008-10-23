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

import com.google.inject.jsr250.Jsr250Module;
import com.google.inject.matcher.Matchers;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.MessageDriven;
import org.apache.camel.Routes;
import org.apache.camel.guice.impl.ConsumerInjection;
import org.apache.camel.guice.impl.EndpointInjector;
import org.apache.camel.guice.impl.ProduceInjector;

/**
 * A base Guice module for creating a {@link CamelContext} leaving it up to the users module
 * to bind a Set<Routes> for the routing rules.
 * <p>
 * To bind the routes you should create a provider method annotated with @Provides and returning Set<Routes> such as
 * <code><pre>
 * public class MyModule extends CamelModule {
 *   &#64;Provides
 *   Set&lt;Routes&gt; routes(Injector injector) { ... }
 * }
 * </pre></code>
 * If you wish to bind all of the bound {@link Routes} implementations available - maybe with some filter applied - then
 * please use the {@link org.apache.camel.guice.CamelModuleWithMatchingRoutes}.
 * <p>
 * Otherwise if you wish to list all of the classes of the {@link Routes} implementations then use the
 * {@link org.apache.camel.guice.CamelModuleWithRouteTypes} module instead.
 *
 * @version $Revision$
 */
public class CamelModule extends Jsr250Module {

    protected void configure() {
        super.configure();
        
        bind(CamelContext.class).to(GuiceCamelContext.class).asEagerSingleton();

        bind(EndpointInjector.class);
        bind(ProduceInjector.class);

        ConsumerInjection consumerInjection = new ConsumerInjection();
        requestInjection(consumerInjection);


        bindConstructorInterceptor(Matchers.methodAnnotatedWith(MessageDriven.class), consumerInjection);
        bindConstructorInterceptor(Matchers.methodAnnotatedWith(Consume.class), consumerInjection);
    }

}
