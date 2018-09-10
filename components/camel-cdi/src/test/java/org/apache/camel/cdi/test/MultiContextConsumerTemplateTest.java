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
package org.apache.camel.cdi.test;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.bean.DefaultCamelContextBean;
import org.apache.camel.cdi.bean.FirstCamelContextBean;
import org.apache.camel.cdi.bean.SecondCamelContextBean;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class MultiContextConsumerTemplateTest {

    @Inject
    private CamelContext defaultCamelContext;

    @Inject
    private ConsumerTemplate defaultConsumer;

    @Inject
    private ProducerTemplate defaultProducer;

    @Inject @ContextName("first")
    private CamelContext firstCamelContext;

    @Inject @ContextName("first")
    private ConsumerTemplate firstConsumer;

    @Inject @ContextName("first")
    private ProducerTemplate firstProducer;

    @Inject @ContextName("second")
    private CamelContext secondCamelContext;

    @Inject @ContextName("second")
    private ConsumerTemplate secondConsumer;

    @Inject @ContextName("second")
    private ProducerTemplate secondProducer;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(
                DefaultCamelContextBean.class,
                FirstCamelContextBean.class,
                SecondCamelContextBean.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    @InSequence(1)
    public void configureCamelContexts() throws Exception {
        secondCamelContext.getRouteController().startAllRoutes();
    }

    @Test
    @InSequence(2)
    public void receiveBodyFromDefaultCamelContext() {
        defaultProducer.sendBody("seda:foo", "foo");

        String body = defaultConsumer.receiveBody("seda:foo", TimeUnit.SECONDS.toMillis(1L), String.class);

        assertThat("Body is incorrect!", body, is(equalTo("foo")));
    }

    @Test
    @InSequence(3)
    public void receiveBodyFromFirstCamelContext() {
        firstProducer.sendBody("seda:bar", "bar");

        String body = firstConsumer.receiveBody("seda:bar", TimeUnit.SECONDS.toMillis(1L), String.class);

        assertThat("Body is incorrect!", body, is(equalTo("bar")));
    }

    @Test
    @InSequence(4)
    public void receiveBodyFromSecondCamelContext() {
        secondProducer.sendBody("seda:baz", "baz");

        String body = secondConsumer.receiveBody("seda:baz", TimeUnit.SECONDS.toMillis(1L), String.class);

        assertThat("Body is incorrect!", body, is(equalTo("baz")));
    }
}
