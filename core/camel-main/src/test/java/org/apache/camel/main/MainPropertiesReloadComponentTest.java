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

import org.apache.camel.component.log.LogComponent;
import org.apache.camel.util.OrderedLocationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A properties file that changed in dev mode configures its components again: a component is created anew, once per
 * reload, and gets every option of the reload, not only the last one.
 */
public class MainPropertiesReloadComponentTest {

    @Test
    public void allTheOptionsOfAComponentInOneReloadAreSet() throws Exception {
        Main main = main(new Properties());
        try {
            main.start();

            reload(main).onChanged("file:application.properties", properties(
                    "camel.component.log.source-location-logger-name", "true",
                    "camel.component.log.lazy-start-producer", "true"));

            LogComponent log = main.getCamelContext().getComponent("log", LogComponent.class);
            assertThat(log.isSourceLocationLoggerName()).isTrue();
            assertThat(log.isLazyStartProducer()).isTrue();
        } finally {
            main.stop();
        }
    }

    @Test
    public void anOptionThatDidNotChangeIsKept() throws Exception {
        Properties startup = new Properties();
        startup.setProperty("camel.component.log.source-location-logger-name", "true");
        startup.setProperty("camel.component.log.lazy-start-producer", "true");
        Main main = main(startup);
        try {
            main.start();
            assertThat(main.getCamelContext().getComponent("log", LogComponent.class).isSourceLocationLoggerName()).isTrue();

            // the file changed one option of the component
            reload(main).onChanged("file:application.properties", properties(
                    "camel.component.log.lazy-start-producer", "false"));

            LogComponent log = main.getCamelContext().getComponent("log", LogComponent.class);
            assertThat(log.isLazyStartProducer()).isFalse();
            assertThat(log.isSourceLocationLoggerName()).isTrue();
        } finally {
            main.stop();
        }
    }

    private static Main main(Properties properties) {
        Main main = new Main();
        main.setDefaultPropertyPlaceholderLocation("false");
        main.setOverrideProperties(properties);
        return main;
    }

    private static MainPropertiesReload reload(Main main) {
        MainPropertiesReload reload = new MainPropertiesReload(main);
        reload.setCamelContext(main.getCamelContext());
        return reload;
    }

    private static OrderedLocationProperties properties(String... keysAndValues) {
        OrderedLocationProperties answer = new OrderedLocationProperties();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            answer.put("file:application.properties", keysAndValues[i], keysAndValues[i + 1]);
        }
        return answer;
    }
}
