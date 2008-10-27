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

import java.util.Hashtable;

import javax.naming.InitialContext;

import junit.framework.TestCase;
import com.google.inject.Injector;
import com.google.inject.Provides;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.guiceyfruit.Injectors;
import org.guiceyfruit.jndi.GuiceInitialContextFactory;
import org.guiceyfruit.jndi.JndiBind;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Lets use a custom CamelModule to perform explicit binding of route builders
 *
 * @version $Revision$
 */
public class ComponentFoundInRegistryTest extends TestCase {

    public static class MyModule extends CamelModuleWithMatchingRoutes {
        @Provides
        @JndiBind("foo")
        MockComponent foo() {
            return new MockComponent();
        }
    }

    public void testGuice() throws Exception {
        Hashtable env = new Hashtable();
        env.put(InitialContext.PROVIDER_URL, GuiceInitialContextFactory.class.getName());
        env.put(Injectors.MODULE_CLASS_NAMES, MyModule.class.getName());

        InitialContext context = new InitialContext(env);
        Injector injector = (Injector) context.lookup(Injector.class.getName());
        assertNotNull("Found injector", injector);

        Object value = context.lookup("foo");
        assertNotNull("Should have found a value for foo!", value);

        CamelContext camelContext = injector.getInstance(CamelContext.class);
        Component component = camelContext.getComponent("foo");
        assertThat(component, is(MockComponent.class));

        Endpoint endpoint = camelContext.getEndpoint("foo:cheese");
        assertThat(endpoint, is(MockEndpoint.class));
    }

}