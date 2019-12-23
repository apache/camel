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

import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.seda.BlockingQueueFactory;
import org.apache.camel.component.seda.PriorityBlockingQueueFactory;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.Assert;
import org.junit.Test;

public class MainIoCAutowireTest extends Assert {
    @Test
    public void autowireNonNullOnlyDisabledTest() {
        Main main = new Main();

        try {
            main.bind("seda", createSedaComponent());
            main.addConfigurationClass(MyConfiguration.class);
            main.configure().setAutowireComponentPropertiesNonNullOnly(false);
            main.setPropertyPlaceholderLocations("empty.properties");
            main.start();

            BlockingQueueFactory qf = main.getCamelContext().getComponent("seda", SedaComponent.class).getDefaultQueueFactory();
            assertTrue(qf instanceof PriorityBlockingQueueFactory);
        } finally {
            main.stop();
        }
    }

    @Test
    public void autowireNonNullOnlyEnabledTest() {
        Main main = new Main();

        try {
            main.bind("seda", createSedaComponent());
            main.addConfigurationClass(MyConfiguration.class);
            main.configure().setAutowireComponentPropertiesNonNullOnly(true);
            main.setPropertyPlaceholderLocations("empty.properties");
            main.start();

            BlockingQueueFactory qf = main.getCamelContext().getComponent("seda", SedaComponent.class).getDefaultQueueFactory();
            assertTrue(qf instanceof MySedaBlockingQueueFactory);
        } finally {
            main.stop();
        }
    }

    @Test
    public void doNotAutowireContextTest() {
        Main main = new Main();

        try {
            DefaultCamelContext otherContext = new DefaultCamelContext();
            otherContext.setName("other-ctx");

            main.bind("dummy", new MyDummyComponent());
            main.bind("context", otherContext);
            main.addConfigurationClass(MyConfiguration.class);
            main.configure().setName("main");
            main.configure().setAutowireComponentPropertiesNonNullOnly(true);
            main.setPropertyPlaceholderLocations("empty.properties");
            main.start();

            MyDummyComponent component = main.getCamelContext().getComponent("dummy", MyDummyComponent.class);
            // the camel context is bound to the component upon initialization
            assertEquals(main.getCamelContext(), component.getCamelContext());
            // the camel context should not be set by auto wiring
            assertEquals(null, component.getConfig().getCamelContext());
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

    public static SedaComponent createSedaComponent() {
        SedaComponent seda = new SedaComponent();
        seda.setDefaultQueueFactory(new MySedaBlockingQueueFactory());

        return seda;
    }


    public static class MyDummyComponent extends DefaultComponent {
        private MyDummyConfig config = new MyDummyConfig();

        public MyDummyConfig getConfig() {
            return config;
        }

        public void setConfig(MyDummyConfig config) {
            this.config = config;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new DefaultEndpoint() {
                @Override
                public Producer createProducer() throws Exception {
                    return new DefaultAsyncProducer(this) {
                        @Override
                        public boolean process(Exchange exchange, AsyncCallback callback) {
                            return false;
                        }
                    };
                }

                @Override
                public Consumer createConsumer(Processor processor) throws Exception {
                    return new DefaultConsumer(this, processor);
                }

                @Override
                protected String createEndpointUri() {
                    return "dummy://foo";
                }
            };
        }
    }

    public static class MyDummyConfig implements CamelContextAware {
        private CamelContext camelContext;

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }
    }
}
