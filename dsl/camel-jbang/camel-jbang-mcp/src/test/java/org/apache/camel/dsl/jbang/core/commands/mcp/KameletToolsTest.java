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

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-25640: KameletTools bugs — no caching and limit-before-sort.
 * <p>
 * All tests in this class download {@code camel-kamelets-catalog} from Maven Central (the module has no compile-time
 * dependency on it, so the download is unavoidable). They are disabled in CI to avoid network failures.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Runs only local — requires camel-kamelets-catalog download from Maven Central")
class KameletToolsTest {

    private final KameletTools tools = new KameletTools();

    /** CAMEL-25640: repeated calls must return identical, sorted results (no HashMap iteration order). */
    @Test
    void repeatedCallsReturnTheSameSortedList() {
        KameletTools.KameletListResult first = tools.camel_catalog_kamelets(null, null, null, null);
        KameletTools.KameletListResult second = tools.camel_catalog_kamelets(null, null, null, null);

        assertThat(first.kamelets()).isEqualTo(second.kamelets());
        // kamelets are sorted by name
        List<String> names = first.kamelets().stream().map(KameletTools.KameletInfo::name).toList();
        List<String> sorted = names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        assertThat(names).isEqualTo(sorted);
    }

    /** CAMEL-25640: limit is applied after sorting, so the first N kamelets alphabetically are returned. */
    @Test
    void limitIsAppliedAfterSorting() {
        KameletTools.KameletListResult full = tools.camel_catalog_kamelets(null, null, null, null);
        assertThat(full.kamelets()).hasSizeGreaterThan(5);

        KameletTools.KameletListResult limited = tools.camel_catalog_kamelets(null, null, 3, null);
        assertThat(limited.count()).isEqualTo(3);
        // total must reflect the full set size, not the limited subset
        assertThat(limited.total()).isEqualTo(full.total());
        // the 3 kamelets returned must be the first 3 alphabetically of the full set
        assertThat(limited.kamelets())
                .isEqualTo(full.kamelets().subList(0, 3));
    }

    /** CAMEL-25640: total reflects all matching kamelets, not just the returned page. */
    @Test
    void totalReflectsAllMatchingKamelets() {
        KameletTools.KameletListResult all = tools.camel_catalog_kamelets(null, null, null, null);
        KameletTools.KameletListResult paged = tools.camel_catalog_kamelets(null, null, 1, null);

        assertThat(paged.count()).isEqualTo(1);
        assertThat(paged.total()).isEqualTo(all.total());
    }

    @Test
    void filterByTypeReturnsOnlyMatchingType() {
        KameletTools.KameletListResult sources = tools.camel_catalog_kamelets(null, "source", null, null);
        assertThat(sources.kamelets()).isNotEmpty();
        assertThat(sources.kamelets()).allMatch(k -> "source".equalsIgnoreCase(k.type()));
    }

    @Test
    void filterByNameOrDescriptionSubstring() {
        KameletTools.KameletListResult aws = tools.camel_catalog_kamelets("aws-s3", null, null, null);
        assertThat(aws.kamelets()).isNotEmpty();
        assertThat(aws.kamelets()).allMatch(
                k -> k.name() != null && k.name().toLowerCase().contains("aws-s3")
                        || k.description() != null && k.description().toLowerCase().contains("aws-s3"));
    }

    @Test
    void kameletDocReturnsDetails() {
        KameletTools.KameletDetailResult detail = tools.camel_catalog_kamelet_doc("log-sink", null);
        assertThat(detail.name()).isEqualTo("log-sink");
        assertThat(detail.type()).isEqualTo("sink");
        assertThat(detail.options()).isNotEmpty();
    }

    @Test
    void kameletDocForUnknownNameThrows() {
        assertThatThrownBy(() -> tools.camel_catalog_kamelet_doc("no-such-kamelet-xyz", null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("no-such-kamelet-xyz");
    }

    @Test
    void kameletDocForBlankNameThrows() {
        assertThatThrownBy(() -> tools.camel_catalog_kamelet_doc("", null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    /** CAMEL-25640: second call uses the cache, not a fresh loadKamelets(). */
    @Test
    void secondCallIsServedFromCache() {
        // two calls with the same version — should not throw and should be equal
        KameletTools.KameletListResult r1 = tools.camel_catalog_kamelets(null, null, null, null);
        KameletTools.KameletListResult r2 = tools.camel_catalog_kamelets(null, null, null, null);
        assertThat(r1.kamelets()).isEqualTo(r2.kamelets());
        assertThat(r1.kameletsVersion()).isEqualTo(r2.kameletsVersion());
    }
}
