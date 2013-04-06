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

import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Named;

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

/**
 * Create a collection of routes via a provider method
 *
 * @version 
 */
public class EndpointInjectionTest extends Assert {

    public static class MyModule extends CamelModuleWithMatchingRoutes {

        @Override
        protected void configure() {
            super.configure();
            bind(MyBean.class);
        }

        @Provides
        @Named("foo")
        protected Collection<? extends RoutesBuilder> myRoutes() {
            return Lists.newArrayList(new MyConfigurableRoute2("direct:a", "direct:b"), new MyConfigurableRoute2("direct:c", "direct:d"));
        }
    }

    public static class MyBean {
        @EndpointInject(uri = "mock:foo")
        MockEndpoint endpoint;
    }

    @Test
    public void testGuice() throws Exception {
        Injector injector = Guice.createInjector(new MyModule());

        MyBean bean = injector.getInstance(MyBean.class);
        assertNotNull("bean.endpoint", bean.endpoint);
        assertEquals("bean.endpoint.uri", "mock://foo", bean.endpoint.getEndpointUri());

        GuiceTest.assertCamelContextRunningThenCloseInjector(injector);
    }

}