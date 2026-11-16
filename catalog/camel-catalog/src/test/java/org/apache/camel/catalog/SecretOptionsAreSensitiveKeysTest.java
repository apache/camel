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
package org.apache.camel.catalog;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.util.SensitiveUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every option flagged secret in the catalog must be a sensitive key, so its value is masked wherever Camel masks
 * sensitive keys (such as the Camel Main auto-configuration summary).
 */
class SecretOptionsAreSensitiveKeysTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    @Test
    void everySecretMainOptionIsASensitiveKey() {
        Set<String> missing = new TreeSet<>();
        collectMissing("main", CATALOG.mainModel().getOptions(), missing);

        assertTrue(missing.isEmpty(), "Secret options missing from the sensitive keys: " + missing);
    }

    @Test
    void everySecretComponentDataFormatLanguageAndBeanOptionIsASensitiveKey() {
        Set<String> missing = new TreeSet<>();
        for (String name : CATALOG.findComponentNames()) {
            collectMissing("component:" + name, CATALOG.componentModel(name).getComponentOptions(), missing);
        }
        for (String name : CATALOG.findDataFormatNames()) {
            collectMissing("dataformat:" + name, CATALOG.dataFormatModel(name).getOptions(), missing);
        }
        for (String name : CATALOG.findLanguageNames()) {
            collectMissing("language:" + name, CATALOG.languageModel(name).getOptions(), missing);
        }
        for (String name : CATALOG.findBeansNames()) {
            collectMissing("bean:" + name, CATALOG.pojoBeanModel(name).getOptions(), missing);
        }

        assertTrue(missing.isEmpty(), "Secret options missing from the sensitive keys: " + missing);
    }

    private static void collectMissing(String owner, List<? extends BaseOptionModel> options, Set<String> missing) {
        for (BaseOptionModel option : options) {
            if (option.isSecret() && !SensitiveUtils.containsSensitive(option.getName())) {
                missing.add(owner + " " + option.getName());
            }
        }
    }
}
