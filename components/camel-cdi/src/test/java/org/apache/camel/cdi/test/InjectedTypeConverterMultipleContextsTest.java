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
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.apache.camel.CamelContext;
import org.apache.camel.Converter;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.FirstCamelContextConvertingRoute;
import org.apache.camel.cdi.bean.SecondCamelContextConvertingRoute;
import org.apache.camel.cdi.pojo.TypeConverterInput;
import org.apache.camel.cdi.pojo.TypeConverterOutput;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.cdi.expression.ExchangeExpression.fromCamelContext;
import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class InjectedTypeConverterMultipleContextsTest {

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
                // Camel CDI
                .addPackage(CdiCamelExtension.class.getPackage())
                // Test class
                .addClass(FirstCamelContextConvertingRoute.class)
                .addClass(SecondCamelContextConvertingRoute.class)
                // Type converter
                .addClass(InjectedTypeConverter.class)
                // No need as Camel CDI automatically registers the type converter bean
                //.addAsManifestResource(new StringAsset("org.apache.camel.cdi.se.converter"), ArchivePaths.create("services/org/apache/camel/TypeConverter"))
                // Bean archive deployment descriptor
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Produces
    @ContextName("first")
    @ApplicationScoped
    public CamelContext camelContextFoo() {
        return new DefaultCamelContext();
    }

    @Produces
    @ContextName("second")
    @ApplicationScoped
    public CamelContext camelContextBar() {
        return new DefaultCamelContext();
    }

    @Test
    public void sendMessageToInboundFirst(@ContextName("first") @Uri("direct:inbound") ProducerTemplate inbound,
                                          @ContextName("first") @Uri("mock:outbound") MockEndpoint outbound) throws InterruptedException {
        sendMessageToInbound(inbound, outbound, "first");
    }

    @Test
    public void sendMessageToInboundSecond(@ContextName("second") @Uri("direct:inbound") ProducerTemplate inbound,
                                           @ContextName("second") @Uri("mock:outbound") MockEndpoint outbound) throws InterruptedException {
        sendMessageToInbound(inbound, outbound, "second");
    }


    private void sendMessageToInbound(ProducerTemplate inbound, MockEndpoint outbound, String contextName) throws InterruptedException {
        outbound.expectedMessageCount(1);
        outbound.message(0).exchange().matches(fromCamelContext(contextName));

        TypeConverterInput input = new TypeConverterInput();
        input.setProperty("test");

        inbound.sendBody(input);

        assertIsSatisfied(2L, TimeUnit.SECONDS, outbound);
        assertThat(outbound.getExchanges().get(0).getIn().getBody(TypeConverterOutput.class).getProperty(), is(equalTo("test")));
    }

    @Converter
    public static final class InjectedTypeConverter {
        @Converter
        public TypeConverterOutput convert(TypeConverterInput input) throws Exception {
            TypeConverterOutput output = new TypeConverterOutput();
            output.setProperty(input.getProperty());
            return output;
        }
    }

}

