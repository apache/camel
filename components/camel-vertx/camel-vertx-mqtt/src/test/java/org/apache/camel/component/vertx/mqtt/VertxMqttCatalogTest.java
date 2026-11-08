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
package org.apache.camel.component.vertx.mqtt;

import java.util.Locale;
import java.util.Map;

import org.apache.camel.CatalogCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog of the component as tools read it: a plain context finds it, and it offers no option that lowers the TLS
 * checks.
 */
class VertxMqttCatalogTest {

    @Test
    void theCatalogOffersNoOptionThatLowersTheTlsChecks() throws Exception {
        JsonObject catalog = catalog();
        Map<?, ?> properties = (Map<?, ?>) catalog.get("properties");
        assertTrue(properties.containsKey("brokerUrl"), properties.keySet().toString());
        assertTrue(properties.containsKey("sslContextParameters"), properties.keySet().toString());
        for (Object name : properties.keySet()) {
            String option = name.toString().toLowerCase(Locale.ROOT);
            assertFalse(option.contains("trustall"), option);
            assertFalse(option.contains("hostnameverif"), option);
            assertFalse(option.contains("proxy"), option);
        }
    }

    @Test
    void theCatalogDescribesTheComponent() throws Exception {
        JsonObject catalog = catalog();
        Map<?, ?> component = (Map<?, ?>) catalog.get("component");
        assertEquals("vertx-mqtt", component.get("scheme"));
        assertEquals("vertx-mqtt:topic", component.get("syntax"));
        Map<?, ?> headers = (Map<?, ?>) catalog.get("headers");
        assertTrue(headers.containsKey(VertxMqttConstants.OVERRIDE_TOPIC), headers.keySet().toString());
        Map<?, ?> properties = (Map<?, ?>) catalog.get("properties");
        Map<?, ?> password = (Map<?, ?>) properties.get("password");
        assertEquals(Boolean.TRUE, password.get("secret"));
    }

    private static JsonObject catalog() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.build();
            String json = ((CatalogCamelContext) context).getComponentParameterJsonSchema(VertxMqttConstants.SCHEME);
            assertNotNull(json, "no catalog JSON for " + VertxMqttConstants.SCHEME);
            return (JsonObject) Jsoner.deserialize(json);
        }
    }
}
