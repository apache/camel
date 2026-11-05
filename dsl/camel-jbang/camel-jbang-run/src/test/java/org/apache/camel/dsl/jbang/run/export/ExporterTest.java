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
package org.apache.camel.dsl.jbang.run.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExporterTest {

    @Test
    public void shouldBuildRepositoryListWithIdUrlFormat() {
        var repos = Exporter.buildRepositoryList(
                "atlassian=https://packages.atlassian.com/maven-external/,https://other.repo/releases");

        assertThat(repos).hasSize(2);
        assertThat(repos.get(0))
                .containsEntry("id", "atlassian")
                .containsEntry("url", "https://packages.atlassian.com/maven-external/");
        assertThat(repos.get(1))
                .containsEntry("id", "custom1")
                .containsEntry("url", "https://other.repo/releases");
    }
}
