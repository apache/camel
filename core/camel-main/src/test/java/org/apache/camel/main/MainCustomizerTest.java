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
import org.apache.camel.CamelConfiguration;
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.support.CustomizersSupport;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainCustomizerTest {
    @Test
    public void testComponentCustomizer() {
        Main main = new Main();

        try {
            main.configure().addConfiguration(MyConfiguration.class);
            main.start();

            LogComponent component = main.getCamelContext().getComponent("log", LogComponent.class);
            assertTrue(component.getExchangeFormatter() instanceof MyFormatter);
        } finally {
            main.stop();
        }
    }

    @Test
    public void testComponentCustomizerDisabledWithPolicy() {
        Main main = new Main();

        try {
            main.bind("my-filter", ComponentCustomizer.Policy.none());
            main.configure().addConfiguration(MyConfiguration.class);
            main.start();

            LogComponent component = main.getCamelContext().getComponent("log", LogComponent.class);
            assertFalse(component.getExchangeFormatter() instanceof MyFormatter);
        } finally {
            main.stop();
        }
    }

    @Test
    public void testComponentCustomizerDisabledWithProperties() {
        Main main = new Main();

        try {
            main.addInitialProperty("camel.customizer.component.log.enabled", "false");
            main.bind("my-filter", ComponentCustomizer.Policy.any());
            main.configure().addConfiguration(MyConfiguration.class);
            main.start();

            LogComponent component = main.getCamelContext().getComponent("log", LogComponent.class);
            assertFalse(component.getExchangeFormatter() instanceof MyFormatter);
        } finally {
            main.stop();
        }
    }

    // ****************************
    //
    // Helpers
    //
    // ****************************

    public static class MyConfiguration implements CamelConfiguration {
        @BindToRegistry
        public ComponentCustomizer logCustomizer() {
            return ComponentCustomizer.builder(LogComponent.class)
                    .build(component -> component.setExchangeFormatter(new MyFormatter()));
        }

        @BindToRegistry
        public ComponentCustomizer.Policy componentCustomizationPolicy() {
            return new CustomizersSupport.ComponentCustomizationEnabledPolicy();
        }
    }

    public static class MyFormatter extends DefaultExchangeFormatter {
    }
}
