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
package org.apache.camel.guice.consume;

import junit.framework.Assert;
import org.apache.camel.Produce;
import org.apache.camel.Consume;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.guice.CamelModuleWithMatchingRoutes;
import org.apache.camel.guice.produce.MyListener;
import org.apache.camel.guice.produce.MyListenerService;
import org.guiceyfruit.testing.junit3.GuiceyFruitTestCase;
import com.google.inject.Inject;

/**
 * @version $Revision: 697494 $
 */
public class ConsumeTest extends GuiceyFruitTestCase {

    @Inject
    protected MyBean bean;
    @Produce
    protected ProducerTemplate template;

    protected Object expectedBody = "<hello>world!</hello>";

    public void testConsumingWorks() throws Exception {
        template.sendBody("direct:start", expectedBody);

        // lets check the bean has been invoked!
        assertEquals("Body of bean", expectedBody, bean.body);
    }

    public static class Configuration extends CamelModuleWithMatchingRoutes {
        @Override
        protected void configure() {
            super.configure();

            bind(MyBean.class).asEagerSingleton();
        }
    }

    public static class MyBean {
        public String body;

        @Consume(uri = "direct:start")
        public void myHandler(String body) {
            this.body = body;
        }
    }
}