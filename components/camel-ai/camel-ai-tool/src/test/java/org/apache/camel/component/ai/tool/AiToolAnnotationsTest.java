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
package org.apache.camel.component.ai.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiToolAnnotationsTest {

    @Test
    void shouldReturnNullWhenNoHintsConfigured() {
        AiToolConfiguration configuration = new AiToolConfiguration();

        assertThat(AiToolAnnotations.fromConfiguration(configuration)).isNull();
    }

    @Test
    void shouldBuildAnnotationsFromConfiguration() {
        AiToolConfiguration configuration = new AiToolConfiguration();
        configuration.setTitle("Delete order");
        configuration.setReadOnlyHint(false);
        configuration.setDestructiveHint(true);
        configuration.setIdempotentHint(false);
        configuration.setOpenWorldHint(true);

        AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);

        assertThat(annotations).isNotNull();
        assertThat(annotations.title()).isEqualTo("Delete order");
        assertThat(annotations.readOnlyHint()).isFalse();
        assertThat(annotations.destructiveHint()).isTrue();
        assertThat(annotations.idempotentHint()).isFalse();
        assertThat(annotations.openWorldHint()).isTrue();
        assertThat(annotations.returnDirect()).isNull();
    }

    @Test
    void shouldBuildReturnDirectHint() {
        AiToolConfiguration configuration = new AiToolConfiguration();
        configuration.setReturnDirect(true);

        AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);

        assertThat(annotations).isNotNull();
        assertThat(annotations.returnDirect()).isTrue();
        assertThat(annotations.isReturnDirect()).isTrue();
    }

    @Test
    void shouldIgnoreBlankTitle() {
        AiToolConfiguration configuration = new AiToolConfiguration();
        configuration.setTitle("   ");
        configuration.setReadOnlyHint(true);

        AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);

        assertThat(annotations).isNotNull();
        assertThat(annotations.title()).isNull();
        assertThat(annotations.readOnlyHint()).isTrue();
    }

    @Test
    void shouldBuildTitleOnlyAnnotations() {
        AiToolConfiguration configuration = new AiToolConfiguration();
        configuration.setTitle("Lookup customer");

        AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);

        assertThat(annotations).isNotNull();
        assertThat(annotations.title()).isEqualTo("Lookup customer");
        assertThat(annotations.readOnlyHint()).isNull();
        assertThat(annotations.destructiveHint()).isNull();
        assertThat(annotations.idempotentHint()).isNull();
        assertThat(annotations.openWorldHint()).isNull();
    }

    @Test
    void shouldDefaultReturnDirectToNullInFiveArgConstructor() {
        AiToolAnnotations annotations = new AiToolAnnotations("Lookup", true, false, true, false);

        assertThat(annotations.returnDirect()).isNull();
        assertThat(annotations.isReturnDirect()).isFalse();
    }

    @Test
    void shouldBuildPartialBooleanHintsOnly() {
        AiToolConfiguration configuration = new AiToolConfiguration();
        configuration.setDestructiveHint(true);

        AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);

        assertThat(annotations).isNotNull();
        assertThat(annotations.title()).isNull();
        assertThat(annotations.destructiveHint()).isTrue();
        assertThat(annotations.readOnlyHint()).isNull();
    }
}
