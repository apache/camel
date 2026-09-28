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
package org.apache.camel.support;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25032: the properties of one batch of changed files are reloaded before the routes are, so a route saved
 * together with a property it uses is built with that property in place instead of failing on it.
 */
public class FileWatcherReloadOrderTest {

    private static List<String> order(String... names) {
        List<File> files = new ArrayList<>();
        for (String n : names) {
            files.add(new File(n));
        }
        FileWatcherResourceReloadStrategy.orderPropertiesFirst(files);
        return files.stream().map(File::getName).toList();
    }

    @Test
    public void testPropertiesComeFirst() {
        assertThat(order("shop.camel.yaml", "application.properties"))
                .containsExactly("application.properties", "shop.camel.yaml");
    }

    @Test
    public void testPropertiesAlreadyFirstStayFirst() {
        assertThat(order("application.properties", "shop.camel.yaml"))
                .containsExactly("application.properties", "shop.camel.yaml");
    }

    @Test
    public void testTheOrderWithinAKindIsKept() {
        // a stable sort: two properties files, and two route files, keep the order they were reported in
        assertThat(order("b.camel.yaml", "application.properties", "a.camel.yaml", "application-prod.properties"))
                .containsExactly("application.properties", "application-prod.properties", "b.camel.yaml",
                        "a.camel.yaml");
    }

    @Test
    public void testNoPropertiesIsUnchanged() {
        assertThat(order("b.camel.yaml", "a.camel.yaml", "Bean.java"))
                .containsExactly("b.camel.yaml", "a.camel.yaml", "Bean.java");
    }
}
