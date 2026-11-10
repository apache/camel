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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * run/properties-and-profiles: the timer of the route runs every {{welcome.period}}, which application.properties sets;
 * About shows the value the integration runs with.
 */
class PlaceholdersTest {

    @Test
    void theValuesTheIntegrationRunsWith() {
        IntegrationInfo info = new IntegrationInfo();
        add(info, "welcome.period", "3000");
        add(info, "report.schedule", "0/10 * * * * ?");

        assertThat(Placeholders.resolve("timer:welcome (period={{welcome.period}})", info))
                .isEqualTo("timer:welcome (period=3000)");
        assertThat(Placeholders.resolve("cron:report (schedule={{ report.schedule }})", info))
                .isEqualTo("cron:report (schedule=0/10 * * * * ?)");
        // one it has no value for: its default, else as written
        assertThat(Placeholders.resolve("timer:t (period={{tick:1000}}, delay={{start}})", info))
                .isEqualTo("timer:t (period=1000, delay={{start}})");
        // a value with a $ or a \\ is taken as it is
        add(info, "odd", "$1\\x");
        assertThat(Placeholders.resolve("{{odd}}", info)).isEqualTo("$1\\x");
        assertThat(Placeholders.resolve("timer:plain", info)).isEqualTo("timer:plain");
        assertThat(Placeholders.resolve(null, info)).isNull();
    }

    private static void add(IntegrationInfo info, String key, String value) {
        ConfigurationTab.ConfigProperty p = new ConfigurationTab.ConfigProperty();
        p.key = key;
        p.value = value;
        info.configProperties.add(p);
    }
}
