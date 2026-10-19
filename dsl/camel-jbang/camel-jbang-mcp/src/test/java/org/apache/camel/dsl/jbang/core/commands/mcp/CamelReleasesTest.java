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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ReleaseModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelReleasesTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog() {
        @Override
        public List<ReleaseModel> camelReleases() {
            return List.of(release("4.22.0", "17,21,25"), release("4.22.1", "17,21,25"), release("4.23.0", "17,21,25"));
        }
    };

    @Test
    void exactAndSameMinorReleases() {
        assertThat(CamelReleases.forVersion(CATALOG, "4.22.0").getVersion()).isEqualTo("4.22.0");
        // a patch release does not change the JDK baseline: the newest release of the same minor version tells it
        assertThat(CamelReleases.forVersion(CATALOG, "4.22.5").getVersion()).isEqualTo("4.22.1");
        assertThat(CamelReleases.forVersion(CATALOG, "4.23.1-SNAPSHOT").getVersion()).isEqualTo("4.23.0");
    }

    @Test
    void noReleaseForAVersionNewerThanTheCatalog() {
        // the catalog only learns a release once it is out, and a new minor version may drop a JDK version
        assertThat(CamelReleases.forVersion(CATALOG, "4.24.0")).isNull();
        assertThat(CamelReleases.forVersion(CATALOG, "4.24.0-SNAPSHOT")).isNull();
        assertThat(CamelReleases.forVersion(CATALOG, "5.0.0")).isNull();
        assertThat(CamelReleases.forVersion(CATALOG, "4.21.0")).isNull();
    }

    private static ReleaseModel release(String version, String jdk) {
        ReleaseModel release = new ReleaseModel();
        release.setVersion(version);
        release.setJdk(jdk);
        return release;
    }
}
