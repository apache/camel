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
package org.apache.camel.component.ai.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiResourceSpecTest {

    @Test
    public void testTextualMimeTypes() {
        assertThat(AiResourceSpec.isTextualMimeType("text/plain")).isTrue();
        assertThat(AiResourceSpec.isTextualMimeType("text/markdown; charset=utf-8")).isTrue();
        assertThat(AiResourceSpec.isTextualMimeType("application/json")).isTrue();
        assertThat(AiResourceSpec.isTextualMimeType("APPLICATION/JSON")).isTrue();
        assertThat(AiResourceSpec.isTextualMimeType("application/vnd.api+json")).isTrue();
        assertThat(AiResourceSpec.isTextualMimeType("image/svg+xml")).isTrue();
        assertThat(AiResourceSpec.isTextualMimeType(null)).isTrue();
        assertThat(AiResourceSpec.isTextualMimeType("  ")).isTrue();
    }

    @Test
    public void testBinaryMimeTypes() {
        assertThat(AiResourceSpec.isTextualMimeType("application/pdf")).isFalse();
        assertThat(AiResourceSpec.isTextualMimeType("image/png")).isFalse();
        assertThat(AiResourceSpec.isTextualMimeType("application/octet-stream")).isFalse();
    }
}
