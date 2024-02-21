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
package org.apache.camel.support;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatCustomizer;
import org.apache.camel.spi.DataFormatFactory;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.apache.camel.util.CollectionHelper.propertiesOf;
import static org.junit.jupiter.api.Assertions.*;

public class CustomizersTest {

    // *****************************
    //
    // Helpers
    //
    // *****************************

    public static Stream<Arguments> disableLanguageCustomizationProperties() {
        return Stream.of(
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true",
                        "camel.customizer.language.enabled", "true",
                        "camel.customizer.language.tokenize.enabled", "false")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true",
                        "camel.customizer.language.enabled", "false")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false")));
    }

    public static Stream<Arguments> enableLanguageCustomizationProperties() {
        return Stream.of(
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false",
                        "camel.customizer.language.enabled", "false",
                        "camel.customizer.language.tokenize.enabled", "true")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false",
                        "camel.customizer.language.enabled", "true")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true")));
    }

    public static Stream<Arguments> disableDataFormatCustomizationProperties() {
        return Stream.of(
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true",
                        "camel.customizer.dataformat.enabled", "true",
                        "camel.customizer.dataformat.my-df.enabled", "false")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true",
                        "camel.customizer.dataformat.enabled", "false")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false")));
    }

    public static Stream<Arguments> enableDataFormatCustomizationProperties() {
        return Stream.of(
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false",
                        "camel.customizer.dataformat.enabled", "false",
                        "camel.customizer.dataformat.my-df.enabled", "true")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false",
                        "camel.customizer.dataformat.enabled", "true")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true")));
    }

    public static Stream<Arguments> disableComponentCustomizationProperties() {
        return Stream.of(
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true",
                        "camel.customizer.component.enabled", "true",
                        "camel.customizer.component.log.enabled", "false")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true",
                        "camel.customizer.component.enabled", "false")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false")));
    }

    public static Stream<Arguments> enableComponentCustomizationProperties() {
        return Stream.of(
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false",
                        "camel.customizer.component.enabled", "false",
                        "camel.customizer.component.log.enabled", "true")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "false",
                        "camel.customizer.component.enabled", "true")),
                Arguments.of(propertiesOf(
                        "camel.customizer.enabled", "true")));
    }

    // *****************************
    //
    // Component
    //
    // *****************************

    @Test
    public void testComponentCustomization() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind(
                "log-customizer",
                ComponentCustomizer.forType(
                        LogComponent.class,
                        target -> target.setExchangeFormatter(new MyExchangeFormatter())));

        assertTrue(context.getComponent("log", LogComponent.class).getExchangeFormatter() instanceof MyExchangeFormatter);
    }

    @Test
    public void testComponentCustomizationWithFilter() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind(
                "customizer-filter",
                ComponentCustomizer.Policy.none());
        context.getCamelContextExtension().getRegistry().bind(
                "log-customizer",
                ComponentCustomizer.forType(
                        LogComponent.class,
                        target -> target.setExchangeFormatter(new MyExchangeFormatter())));

        assertFalse(context.getComponent("log", LogComponent.class).getExchangeFormatter() instanceof MyExchangeFormatter);
    }

    @Test
    public void testComponentCustomizationWithFluentBuilder() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind(
                "log-customizer",
                ComponentCustomizer.forType(
                        LogComponent.class,
                        target -> target.setExchangeFormatter(new MyExchangeFormatter())));

        assertTrue(context.getComponent("log", LogComponent.class).getExchangeFormatter() instanceof MyExchangeFormatter);
    }

    @ParameterizedTest
    @MethodSource("disableComponentCustomizationProperties")
    public void testComponentCustomizationDisabledByProperty(Properties properties) {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setInitialProperties(properties);

        context.getCamelContextExtension().getRegistry().bind(
                "customizer-filter",
                new CustomizersSupport.ComponentCustomizationEnabledPolicy());
        context.getCamelContextExtension().getRegistry().bind(
                "log-customizer",
                ComponentCustomizer.forType(
                        LogComponent.class,
                        target -> target.setExchangeFormatter(new MyExchangeFormatter())));

        assertFalse(context.getComponent("log", LogComponent.class).getExchangeFormatter() instanceof MyExchangeFormatter);
    }

    @ParameterizedTest
    @MethodSource("enableComponentCustomizationProperties")
    public void testComponentCustomizationEnabledByProperty(Properties properties) {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setInitialProperties(properties);

        context.getCamelContextExtension().getRegistry().bind(
                "customizer-filter",
                new CustomizersSupport.ComponentCustomizationEnabledPolicy());
        context.getCamelContextExtension().getRegistry().bind(
                "log-customizer",
                ComponentCustomizer.forType(
                        LogComponent.class,
                        target -> target.setExchangeFormatter(new MyExchangeFormatter())));

        assertTrue(context.getComponent("log", LogComponent.class).getExchangeFormatter() instanceof MyExchangeFormatter);
    }

    // *****************************
    //
    // Data Format
    //
    // *****************************

    @Test
    public void testDataFormatCustomization() {
        AtomicInteger counter = new AtomicInteger();

        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind(
                "my-df",
                (DataFormatFactory) MyDataFormat::new);
        context.getCamelContextExtension().getRegistry().bind(
                "my-df-customizer",
                DataFormatCustomizer.forType(MyDataFormat.class, target -> target.setId(counter.incrementAndGet())));

        DataFormat df1 = context.createDataFormat("my-df");
        DataFormat df2 = context.createDataFormat("my-df");

        assertNotEquals(df1, df2);

        assertTrue(df1 instanceof MyDataFormat);
        assertEquals(1, ((MyDataFormat) df1).getId());
        assertTrue(df2 instanceof MyDataFormat);
        assertEquals(2, ((MyDataFormat) df2).getId());
    }

    @Test
    public void testDataFormatCustomizationWithFilter() {
        AtomicInteger counter = new AtomicInteger();

        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().getRegistry().bind(
                "customizer-filter",
                DataFormatCustomizer.Policy.none());
        context.getCamelContextExtension().getRegistry().bind(
                "my-df",
                (DataFormatFactory) MyDataFormat::new);
        context.getCamelContextExtension().getRegistry().bind(
                "my-df-customizer",
                DataFormatCustomizer.forType(MyDataFormat.class, target -> target.setId(counter.incrementAndGet())));

        DataFormat df1 = context.createDataFormat("my-df");
        DataFormat df2 = context.createDataFormat("my-df");

        assertNotEquals(df1, df2);

        assertTrue(df1 instanceof MyDataFormat);
        assertEquals(0, ((MyDataFormat) df1).getId());
        assertTrue(df2 instanceof MyDataFormat);
        assertEquals(0, ((MyDataFormat) df2).getId());
    }

    @ParameterizedTest
    @MethodSource("disableDataFormatCustomizationProperties")
    public void testDataFormatCustomizationDisabledByProperty(Properties properties) {
        AtomicInteger counter = new AtomicInteger();

        DefaultCamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setInitialProperties(properties);

        context.getCamelContextExtension().getRegistry().bind(
                "customizer-filter",
                new CustomizersSupport.DataFormatCustomizationEnabledPolicy());
        context.getCamelContextExtension().getRegistry().bind(
                "my-df",
                (DataFormatFactory) MyDataFormat::new);
        context.getCamelContextExtension().getRegistry().bind(
                "my-df-customizer",
                DataFormatCustomizer.forType(MyDataFormat.class, target -> target.setId(counter.incrementAndGet())));

        DataFormat df1 = context.resolveDataFormat("my-df");
        assertEquals(0, ((MyDataFormat) df1).getId());
    }

    @ParameterizedTest
    @MethodSource("enableDataFormatCustomizationProperties")
    public void testDataFormatCustomizationEnabledByProperty(Properties properties) {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setInitialProperties(properties);

        context.getCamelContextExtension().getRegistry().bind(
                "customizer-filter",
                new CustomizersSupport.DataFormatCustomizationEnabledPolicy());
        context.getCamelContextExtension().getRegistry().bind(
                "my-df",
                (DataFormatFactory) MyDataFormat::new);
        context.getCamelContextExtension().getRegistry().bind(
                "my-df-customizer",
                DataFormatCustomizer.forType(MyDataFormat.class, target -> target.setId(1)));

        DataFormat df1 = context.resolveDataFormat("my-df");
        assertEquals(1, ((MyDataFormat) df1).getId());
    }

    // *****************************
    //
    // Model
    //
    // *****************************

    public static class MyDataFormat implements DataFormat {
        private int id;

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            return null;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    public static class MyExchangeFormatter extends DefaultExchangeFormatter {
    }
}
