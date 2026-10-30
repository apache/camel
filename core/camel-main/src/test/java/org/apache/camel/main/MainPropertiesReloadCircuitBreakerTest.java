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

import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.util.OrderedLocationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A properties file that changed in dev mode applies its camel.resilience4j.* and camel.faulttolerance.* keys to the
 * default configurations of the circuit breakers, as when the application starts. The routes are created again after
 * the reload, and a circuit breaker reads the defaults when its route is created, so the new values take effect without
 * a restart.
 */
public class MainPropertiesReloadCircuitBreakerTest {

    @Test
    public void theCircuitBreakerKeysOfAChangedPropertiesFileAreApplied() throws Exception {
        Properties startup = properties("camel.resilience4j.sliding-window-size", "100",
                "camel.resilience4j.failure-rate-threshold", "50");
        Main main = main(startup);
        try {
            main.start();
            ModelCamelContext model = (ModelCamelContext) main.getCamelContext();
            assertThat(model.getResilience4jConfiguration(null).getSlidingWindowSize()).isEqualTo("100");

            // application.properties saved in dev mode, with the keys written in either form
            reload(main).onChanged("file:application.properties", properties(
                    "camel.resilience4j.slidingWindowSize", "4",
                    "camel.resilience4j.minimum-number-of-calls", "4",
                    "camel.faulttolerance.requestVolumeThreshold", "6"));

            Resilience4jConfigurationDefinition resilience4j = model.getResilience4jConfiguration(null);
            assertThat(resilience4j.getSlidingWindowSize()).isEqualTo("4");
            assertThat(resilience4j.getMinimumNumberOfCalls()).isEqualTo("4");
            // a key that did not change keeps its value (a float in the main configuration, so "50.0")
            assertThat(Float.parseFloat(resilience4j.getFailureRateThreshold())).isEqualTo(50f);
            // a configuration that the application did not have before is created
            assertThat(model.getFaultToleranceConfiguration(null).getRequestVolumeThreshold()).isEqualTo("6");
        } finally {
            main.stop();
        }
    }

    @Test
    public void aReloadWithoutCircuitBreakerKeysLeavesTheDefaultsAsTheyAre() throws Exception {
        Main main = main(properties("camel.resilience4j.sliding-window-size", "100"));
        try {
            main.start();
            ModelCamelContext model = (ModelCamelContext) main.getCamelContext();

            reload(main).onChanged("file:application.properties", properties("greeting", "hello"));

            assertThat(model.getResilience4jConfiguration(null).getSlidingWindowSize()).isEqualTo("100");
            assertThat(model.getFaultToleranceConfiguration(null)).isNull();
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
