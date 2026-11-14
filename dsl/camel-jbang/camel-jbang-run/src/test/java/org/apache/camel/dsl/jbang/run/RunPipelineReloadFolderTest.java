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
package org.apache.camel.dsl.jbang.run;

import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.run.RunPipeline.ReloadFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The folder dev mode watches for the files given on the command line, and their pattern (CAMEL-26930).
 */
class RunPipelineReloadFolderTest {

    @TempDir
    Path dir;

    @Test
    void filesInTheCurrentDirectory() {
        assertThat(RunPipeline.reloadFolder(List.of("a.camel.yaml", "b.camel.yaml")))
                .isEqualTo(new ReloadFolder(".", "a.camel.yaml,b.camel.yaml", false));
    }

    @Test
    void theAutoDetectedPropertiesMatch() {
        // camel run adds ./application.properties, which the pattern did not match
        assertThat(RunPipeline.reloadFolder(List.of("a.camel.yaml", "./application.properties")))
                .isEqualTo(new ReloadFolder(".", "a.camel.yaml,application.properties", false));
    }

    @Test
    void filesInTwoFoldersWatchTheFolderThatHasBoth() {
        // the folder of other/b.camel.yaml was watched, so a change of a.camel.yaml was not seen
        assertThat(RunPipeline.reloadFolder(List.of("a.camel.yaml", "other/b.camel.yaml", "./application.properties")))
                .isEqualTo(new ReloadFolder(".", "a.camel.yaml,other/b.camel.yaml,application.properties", true));
        assertThat(RunPipeline.reloadFolder(List.of("../x/a.camel.yaml", "../y/b.camel.yaml")))
                .isEqualTo(new ReloadFolder("..", "x/a.camel.yaml,y/b.camel.yaml", true));
    }

    @Test
    void filesInOneOtherFolderWatchThatFolder() {
        assertThat(RunPipeline.reloadFolder(List.of("routes/a.camel.yaml", "routes/b.camel.yaml")))
                .isEqualTo(new ReloadFolder("routes", "a.camel.yaml,b.camel.yaml", false));
    }

    @Test
    void theFilesOfTheCliDoNotWidenTheFolder() {
        assertThat(RunPipeline.reloadFolder(List.of("routes/a.camel.yaml", ".camel-jbang/generated.yaml")).directory())
                .isEqualTo("routes");
    }

    @Test
    void anAbsolutePathGivesAnAbsoluteFolder() {
        String a = dir.resolve("x/a.camel.yaml").toString();
        String b = dir.resolve("y/b.camel.yaml").toString();
        assertThat(RunPipeline.reloadFolder(List.of(a, b)))
                .isEqualTo(new ReloadFolder(dir.toString(), "x/a.camel.yaml,y/b.camel.yaml", true));
    }
}
