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

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27340: the Classpath, CVE Audit and Heap Histogram tabs see the JARs camel run downloads, which the jvm action
 * returns as applicationClasspath.
 */
class ClasspathOfTest {

    @Test
    void theJvmClasspathAndTheApplicationClassLoader() throws Exception {
        JsonObject reply = (JsonObject) Jsoner.deserialize("""
                {"classpath": ["/r/a.jar", "/r/b.jar"], "applicationClasspath": ["/r/b.jar", "/r/commons-text-1.9.jar"]}
                """);
        assertThat(ClasspathTab.classpathOf(reply)).containsExactly("/r/a.jar", "/r/b.jar", "/r/commons-text-1.9.jar");
    }

    @Test
    void anOlderIntegrationHasTheJvmClasspathOnly() throws Exception {
        JsonObject reply = (JsonObject) Jsoner.deserialize("{\"classpath\": [\"/r/a.jar\"]}");
        assertThat(ClasspathTab.classpathOf(reply)).containsExactly("/r/a.jar");
        assertThat(ClasspathTab.classpathOf(null)).isEmpty();
    }
}
