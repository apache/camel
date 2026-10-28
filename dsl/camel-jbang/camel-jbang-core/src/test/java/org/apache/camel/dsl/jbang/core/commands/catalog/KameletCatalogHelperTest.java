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
package org.apache.camel.dsl.jbang.core.commands.catalog;

import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class KameletCatalogHelperTest {

    @Test
    void releasedCatalogIsLoadedOnce() throws Exception {
        String version = VersionHelper.extractKameletsVersion();
        assumeFalse(version.endsWith("-SNAPSHOT"), "a snapshot catalog is loaded every time");

        // loading the catalog scans the classpath, so a JVM that runs several commands loads a released one once
        Map<String, Object> first = KameletCatalogHelper.loadKamelets(version, null);
        Map<String, Object> second = KameletCatalogHelper.loadKamelets(version, null);

        assertThat(first).containsKey("timer-source");
        assertThat(second).isSameAs(first);
        // the callers share it
        assertThatThrownBy(() -> first.remove("timer-source")).isInstanceOf(UnsupportedOperationException.class);
    }
}
