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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * --exclude for the files of a source directory: camel run ., --source-dir and camel dev (CAMEL-26930).
 */
class RunPipelineExcludeTest {

    @Test
    void theExcludesAreUnderTheSourceDirectory() {
        // the files of the directory have the location file:./sub/b.camel.yaml
        assertThat(RunPipeline.sourceDirExcludePattern(".", List.of("sub/**", "./skip.camel.yaml", "*.xml")))
                .isEqualTo("file:./sub/**,file:./**/skip.camel.yaml,file:./**/*.xml");
        assertThat(RunPipeline.sourceDirExcludePattern("routes/", List.of("old/**")))
                .isEqualTo("file:routes/old/**");
    }

    @Test
    void noExcludesNoPattern() {
        assertThat(RunPipeline.sourceDirExcludePattern(".", List.of())).isNull();
        assertThat(RunPipeline.sourceDirExcludePattern(".", null)).isNull();
        assertThat(RunPipeline.sourceDirExcludePattern(".", List.of(" "))).isNull();
    }

    @Test
    void theExcludesAreLeftOutOfTheReload() {
        assertThat(
                RunPipeline.sourceDirReloadPattern(null) + RunPipeline.reloadExcludes(List.of("sub/**", "./skip.camel.yaml")))
                .endsWith(",!sub/**,!skip.camel.yaml");
        assertThat(RunPipeline.reloadExcludes(null)).isEmpty();
    }
}
