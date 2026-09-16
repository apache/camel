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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.Exchange;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24715: the write-time checks carry a few lists the catalog has no metadata for. Each states something about a
 * component or an EIP that a later release can change; this cross-checks them against the catalog and the API so they
 * cannot drift silently. A failure names every entry that drifted.
 */
class ChecksCatalogDriftTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    /**
     * The premise of every entry is that the component does not have that option: the day it does, the hint tells the
     * user to remove or rename an option that works.
     */
    @Test
    void inventedOptionsAreNotComponentOptions() {
        List<String> drifted = new ArrayList<>();
        for (String key : new TreeSet<>(EndpointChecks.INVENTED_OPTIONS.keySet())) {
            String scheme = key.substring(0, key.indexOf(':'));
            String option = key.substring(scheme.length() + 1);
            ComponentModel model = catalog.componentModel(scheme);
            if (model == null) {
                drifted.add(key + ": no component " + scheme + " in the catalog");
                continue;
            }
            Set<String> options = new TreeSet<>();
            model.getEndpointOptions().forEach(o -> options.add(o.getName()));
            model.getComponentOptions().forEach(o -> options.add(o.getName()));
            if (options.contains(option)) {
                drifted.add(key + ": " + option + " is a real option of " + scheme + " now, drop the entry");
            }
        }
        assertThat(drifted).as("INVENTED_OPTIONS entries the catalog contradicts").isEmpty();
    }

    /**
     * The exchange properties of the timer are the Exchange.TIMER_* constants the consumer sets; a name the catalog
     * lists as a header of the component is a header, and the hint would send the user to the wrong place.
     */
    @Test
    void exchangePropertiesAreTheApiConstantsAndNotHeaders() throws Exception {
        Set<String> timerConstants = new TreeSet<>();
        for (Field f : Exchange.class.getFields()) {
            if (f.getName().startsWith("TIMER_") && Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                timerConstants.add((String) f.get(null));
            }
        }
        assertThat(timerConstants).isNotEmpty();
        List<String> drifted = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : HeaderChecks.EXCHANGE_PROPERTIES.entrySet()) {
            String scheme = entry.getKey();
            ComponentModel model = catalog.componentModel(scheme);
            if (model == null) {
                drifted.add(scheme + ": no such component in the catalog");
                continue;
            }
            Set<String> headers = new TreeSet<>();
            model.getEndpointHeaders().forEach(h -> headers.add(h.getName()));
            for (String name : entry.getValue()) {
                if ("timer".equals(scheme) && !timerConstants.contains(name)) {
                    drifted.add(scheme + ": " + name + " is not an Exchange.TIMER_* constant");
                }
                if (headers.contains(name)) {
                    drifted.add(scheme + ": " + name + " is a header in the catalog, not an exchange property");
                }
            }
        }
        assertThat(drifted).as("EXCHANGE_PROPERTIES entries the API or the catalog contradicts").isEmpty();
    }
}
