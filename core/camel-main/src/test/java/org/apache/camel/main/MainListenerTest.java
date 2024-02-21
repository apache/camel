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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.apache.camel.util.CollectionHelper.propertiesOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MainListenerTest {

    @Test
    public void testEventOrder() throws Exception {
        List<String> events = new ArrayList<>();
        Main main = new Main();
        main.addMainListener((MainListener) Proxy.newProxyInstance(
                MainListener.class.getClassLoader(),
                new Class[] { MainListener.class },
                (proxy, method, args) -> {
                    events.add(method.getName());
                    return null;
                }));
        Thread thread = new Thread(() -> {
            try {
                main.run();
            } catch (Exception e) {
                fail("Unexpected exception: " + e);
            }
        });
        thread.start();
        Thread.sleep(100);
        main.completed();
        thread.join();
        assertEquals(Arrays.asList("beforeInitialize", "beforeConfigure", "afterConfigure",
                "beforeStart", "afterStart", "beforeStop", "afterStop"), events);
    }

    @Test
    public void testBeforeConfigure() {
        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("false");
            main.setInitialProperties(propertiesOf(
                    "camel.context.name", "my-ctx"));
            main.addMainListener(new MainListenerSupport() {
                @Override
                public void beforeConfigure(BaseMainSupport main) {
                    main.getCamelContext().getPropertiesComponent().setOverrideProperties(propertiesOf(
                            "camel.context.name", "my-ctx-override"));
                }
            });
            main.start();

            assertEquals("my-ctx-override", main.getCamelContext().getName());
        } finally {
            main.stop();
        }
    }
}
