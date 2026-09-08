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

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.main.support.MyDummyComponent;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.SecretRotationAware;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of what a vault component triggers when it detects a rotated secret: the component option that was
 * configured with a placeholder is re-resolved, and {@link SecretRotationAware} beans are told to re-authenticate.
 */
public class MainContextReloadSecretRotationTest {

    @Test
    public void testRotatedSecretReachesComponentOption() {
        MyVaultFunction vault = new MyVaultFunction();

        Properties properties = new Properties();
        properties.setProperty("camel.main.context-reload-enabled", "true");
        properties.setProperty("camel.component.dummy.component-value", "{{vault:password}}");

        Main main = new Main();
        MyRotationAware bean = new MyRotationAware();
        try {
            main.bind("dummy", new MyDummyComponent(false));
            main.bind("myRotationAware", bean);
            main.setOverrideProperties(properties);
            main.setDefaultPropertyPlaceholderLocation("false");
            main.addMainListener(new MainListenerSupport() {
                @Override
                public void beforeConfigure(BaseMainSupport main) {
                    main.getCamelContext().getPropertiesComponent().addPropertiesFunction(vault);
                }
            });
            main.start();

            CamelContext context = main.getCamelContext();
            MyDummyComponent dummy = (MyDummyComponent) context.getComponent("dummy");
            assertThat(dummy.getComponentValue()).isEqualTo("password-1");
            assertThat(bean.getCounter()).isZero();

            // the secret is rotated in the vault, and the vault component triggers a context reload
            vault.rotate();
            ContextReloadStrategy crs = context.hasService(ContextReloadStrategy.class);
            assertThat(crs).isNotNull();
            crs.onReload("MainContextReloadSecretRotationTest");

            // the component option now holds the rotated secret, and the bean was told to re-authenticate
            MyDummyComponent reloaded = (MyDummyComponent) context.getComponent("dummy");
            assertThat(reloaded.getComponentValue()).isEqualTo("password-2");
            assertThat(bean.getCounter()).isOne();
            assertThat(crs.getLastError()).isNull();
        } finally {
            main.stop();
        }
    }

    private static class MyVaultFunction implements PropertiesFunction {

        private int counter = 1;

        void rotate() {
            counter++;
        }

        @Override
        public String getName() {
            return "vault";
        }

        @Override
        public String apply(String remainder) {
            return remainder + "-" + counter;
        }
    }

    private static class MyRotationAware implements SecretRotationAware {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public void onSecretRotation(Object source) {
            counter.incrementAndGet();
        }

        int getCounter() {
            return counter.get();
        }
    }
}
