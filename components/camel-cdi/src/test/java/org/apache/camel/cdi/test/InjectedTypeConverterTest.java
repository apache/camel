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
package org.apache.camel.cdi.test;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverter;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.InjectedTypeConverterRoute;
import org.apache.camel.cdi.converter.InjectedTestTypeConverter;
import org.apache.camel.cdi.pojo.TypeConverterInput;
import org.apache.camel.cdi.pojo.TypeConverterOutput;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.PropertiesComponent;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class InjectedTypeConverterTest {

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test class
            .addClass(InjectedTypeConverterRoute.class)
            // Type converter
            .addClass(InjectedTestTypeConverter.class)
            // No need as Camel CDI automatically registers the type converter bean
            //.addAsManifestResource(new StringAsset("org.apache.camel.cdi.se.converter"), ArchivePaths.create("services/org/apache/camel/TypeConverter"))
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Produces
    @ApplicationScoped
    @Named("properties")
    private static PropertiesComponent configuration() {
        Properties properties = new Properties();
        properties.put("property1", "value 1");
        properties.put("property2", "value 2");
        PropertiesComponent component = new PropertiesComponent();
        component.setInitialProperties(properties);
        return component;
    }

    @Test
    public void sendMessageToInbound(@Uri("direct:inbound") ProducerTemplate inbound,
                                     @Uri("mock:outbound") MockEndpoint outbound) throws InterruptedException {
        outbound.expectedMessageCount(1);

        TypeConverterInput input = new TypeConverterInput();
        input.setProperty("property value is [{{property1}}]");
        
        inbound.sendBody(input);

        assertIsSatisfied(2L, TimeUnit.SECONDS, outbound);
        assertThat(outbound.getExchanges().get(0).getIn().getBody(TypeConverterOutput.class).getProperty(), is(equalTo("property value is [value 1]")));
    }

    @Test
    public void convertWithTypeConverter(TypeConverter converter) throws NoTypeConversionAvailableException {
        TypeConverterInput input = new TypeConverterInput();
        input.setProperty("property value is [{{property2}}]");

        TypeConverterOutput output = converter.mandatoryConvertTo(TypeConverterOutput.class, input);

        assertThat(output.getProperty(), is(equalTo("property value is [value 2]")));
    }
}
