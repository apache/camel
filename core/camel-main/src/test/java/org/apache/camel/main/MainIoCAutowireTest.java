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
package org.apache.camel.main;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.BlockingQueueFactory;
import org.apache.camel.component.seda.PriorityBlockingQueueFactory;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MainIoCAutowireTest {
    @Test
    public void autowireNonNullOnlyDisabledTest() {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("seda", createSedaComponent());

        Main main = new Main() {
            @Override
            protected CamelContext createCamelContext() {
                return context;
            }
        };

        try {
            main.addConfigurationClass(MyConfiguration.class);
            main.addRouteBuilder(MyRouteBuilder.class);
            main.configure().setAutowireNonNullOnlyComponentProperties(false);
            main.setPropertyPlaceholderLocations("empty.properties");
            main.start();

            BlockingQueueFactory qf = context.getComponent("seda", SedaComponent.class).getDefaultQueueFactory();
            assertThat(qf).isInstanceOf(PriorityBlockingQueueFactory.class);
        } finally {
            main.stop();
        }
    }

    @Test
    public void autowireNonNullOnlyEnabledTest() {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("seda", createSedaComponent());

        Main main = new Main() {
            @Override
            protected CamelContext createCamelContext() {
                return context;
            }
        };

        try {
            main.addConfigurationClass(MyConfiguration.class);
            main.addRouteBuilder(MyRouteBuilder.class);
            main.configure().setAutowireNonNullOnlyComponentProperties(true);
            main.setPropertyPlaceholderLocations("empty.properties");
            main.start();

            BlockingQueueFactory qf = context.getComponent("seda", SedaComponent.class).getDefaultQueueFactory();
            assertThat(qf).isInstanceOf(MySedaBlockingQueueFactory.class);
        } finally {
            main.stop();
        }
    }

    public static class MyConfiguration {
        @BindToRegistry
        public BlockingQueueFactory queueFactory(CamelContext myCamel) {
            // we can optionally include camel context as parameter
            Assert.assertNotNull(myCamel);
            return new PriorityBlockingQueueFactory();
        }
    }

    public static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start").to("mock:results");
        }
    }

    public static SedaComponent createSedaComponent() {
        SedaComponent seda = new SedaComponent();
        seda.setDefaultQueueFactory(new MySedaBlockingQueueFactory());

        return seda;
    }
}
