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
package org.apache.camel.component.chaos;

import java.util.Properties;

import org.apache.camel.test.junit6.CamelContextConfiguration;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Routes with chaos switched on ({@code camel.chaos.enabled=true}) and a fixed seed.
 */
abstract class ChaosTestSupport extends CamelTestSupport {

    @Override
    public void configureContext(CamelContextConfiguration camelContextConfiguration) {
        super.configureContext(camelContextConfiguration);
        Properties props = new Properties();
        props.put("camel.chaos.enabled", "true");
        props.put("camel.chaos.seed", "42");
        camelContextConfiguration.withUseOverridePropertiesWithPropertiesComponent(props);
    }

    ChaosManager chaos() {
        return ChaosManager.get(context);
    }

    /** The report entry of a rule, as JSON. */
    JsonObject ruleReport(String id) {
        JsonArray rules = (JsonArray) chaos().report().toJson().get("rules");
        for (Object o : rules) {
            JsonObject rule = (JsonObject) o;
            if (id.equals(rule.getString("id"))) {
                return rule;
            }
        }
        throw new AssertionError("No rule " + id + " in the report");
    }

    /** How often a rule fired at a hook. */
    long fired(String id, String hook) {
        return ((JsonObject) ruleReport(id).get("fired")).getLong(hook);
    }
}
