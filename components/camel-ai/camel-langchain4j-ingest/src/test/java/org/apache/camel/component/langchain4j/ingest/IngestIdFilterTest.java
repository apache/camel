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
package org.apache.camel.component.langchain4j.ingest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The includeId/excludeId semantics on their own; {@link LangChain4jIngestFilterTest} covers them in the producer.
 */
class IngestIdFilterTest {

    /** An all-blank includeId accepts every id, instead of rejecting every id for lack of a matching pattern. */
    @Test
    void noPatternsAcceptEveryId() {
        assertThat(IngestIdFilter.of(null, null).accepts("docs/guide.md")).isTrue();
        assertThat(IngestIdFilter.of("", "  ").accepts("docs/guide.md")).isTrue();
        assertThat(IngestIdFilter.of(" , ,", ",").accepts("docs/guide.md")).isTrue();
    }

    @Test
    void onlyIdsMatchingAnIncludePatternPass() {
        // blank entries and the spaces around a pattern are ignored
        IngestIdFilter filter = IngestIdFilter.of(" docs/** , ,*.md", null);

        assertThat(filter.accepts("docs/guide.md")).isTrue();
        assertThat(filter.accepts("notes.md")).isTrue();
        assertThat(filter.accepts("notes.txt")).isFalse();
    }

    @Test
    void exclusionWinsOverInclusion() {
        IngestIdFilter filter = IngestIdFilter.of("docs/**", "**/draft-*");

        assertThat(filter.accepts("docs/guide.md")).isTrue();
        assertThat(filter.accepts("docs/draft-plan.md")).isFalse();
        // without includeId, every id not excluded passes
        assertThat(IngestIdFilter.of(null, "**/draft-*").accepts("notes.txt")).isTrue();
    }

    @Test
    void matchingIsCaseSensitiveAndNeedsTheSameLeadingSlash() {
        IngestIdFilter filter = IngestIdFilter.of("docs/**", null);

        assertThat(filter.accepts("Docs/guide.md")).isFalse();
        assertThat(filter.accepts("/docs/guide.md")).isFalse();
        assertThat(IngestIdFilter.of("/docs/**", null).accepts("/docs/guide.md")).isTrue();
    }
}
