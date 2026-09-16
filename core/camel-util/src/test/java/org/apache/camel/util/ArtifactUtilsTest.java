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
package org.apache.camel.util;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactUtilsTest {

    @Test
    public void testComponentArtifact() {
        assertThat(ArtifactUtils.componentArtifact("kafka")).isEqualTo("camel-kafka");
        // the artifact is not always camel-<scheme>
        assertThat(ArtifactUtils.componentArtifact("aws2-ddbstream")).isEqualTo("camel-aws2-ddb");
        assertThat(ArtifactUtils.componentArtifact("class")).isEqualTo("camel-bean");
        // alternative schemes are listed too
        assertThat(ArtifactUtils.componentArtifact("coaps+tcp")).isEqualTo("camel-coap");
        assertThat(ArtifactUtils.componentArtifact("cheese")).isNull();
        assertThat(ArtifactUtils.componentArtifact(null)).isNull();
        assertThat(ArtifactUtils.componentNames()).hasSizeGreaterThan(300).contains("kafka", "direct", "coaps+tcp");
    }

    @Test
    public void testLanguageArtifact() {
        assertThat(ArtifactUtils.languageArtifact("simple")).isEqualTo("camel-core-languages");
        assertThat(ArtifactUtils.languageArtifact("xquery")).isEqualTo("camel-saxon");
        assertThat(ArtifactUtils.languageArtifact("jsonpath")).isEqualTo("camel-jsonpath");
        assertThat(ArtifactUtils.languageArtifact("cheese")).isNull();
        assertThat(ArtifactUtils.languageNames()).hasSizeGreaterThan(20).contains("simple", "xquery", "bean");
    }

    @Test
    public void testDataFormatArtifact() {
        assertThat(ArtifactUtils.dataFormatArtifact("jaxb")).isEqualTo("camel-jaxb");
        assertThat(ArtifactUtils.dataFormatArtifact("jackson")).isEqualTo("camel-jackson");
        assertThat(ArtifactUtils.dataFormatArtifact("cheese")).isNull();
        assertThat(ArtifactUtils.dataFormatNames()).hasSizeGreaterThan(40).contains("jaxb", "csv");
    }

    @Test
    public void testBeans() {
        assertThat(ArtifactUtils.beanJavaType("UseLatestAggregationStrategy"))
                .isEqualTo("org.apache.camel.processor.aggregate.UseLatestAggregationStrategy");
        assertThat(ArtifactUtils.beanInterface("UseLatestAggregationStrategy"))
                .isEqualTo("org.apache.camel.AggregationStrategy");
        assertThat(ArtifactUtils.beanArtifact("UseLatestAggregationStrategy")).isEqualTo("camel-core-processor");
        assertThat(ArtifactUtils.beanArtifact("ZipAggregationStrategy")).isEqualTo("camel-zipfile");
        assertThat(ArtifactUtils.beanArtifact("Cheese")).isNull();
        assertThat(ArtifactUtils.beanJavaType(null)).isNull();
        assertThat(ArtifactUtils.beanNames()).hasSizeGreaterThan(50).contains("MemoryIdempotentRepository");
    }

    @Test
    public void testDependencyHint() {
        assertThat(ArtifactUtils.dependencyHint("camel-saxon")).isEqualTo("add camel-saxon to the classpath");
        // core artifacts are noted as part of camel-core
        assertThat(ArtifactUtils.dependencyHint("camel-core-languages"))
                .isEqualTo("add camel-core-languages (part of camel-core) to the classpath");
        assertThat(ArtifactUtils.dependencyHint("camel-support"))
                .isEqualTo("add camel-support (part of camel-core) to the classpath");
        // camel-base64 is a data format, not a core artifact
        assertThat(ArtifactUtils.dependencyHint("camel-base64")).isEqualTo("add camel-base64 to the classpath");
        assertThat(ArtifactUtils.dependencyHint(null)).isEmpty();
        assertThat(ArtifactUtils.dependencyHint(" ")).isEmpty();
    }

    @Test
    public void testHints() {
        assertThat(ArtifactUtils.componentHint("kafka"))
                .isEqualTo(" (the kafka component is in camel-kafka; add camel-kafka to the classpath)");
        assertThat(ArtifactUtils.componentHint("kafak")).isEqualTo(" (not a built-in Camel component; did you mean 'kafka'?)");
        assertThat(ArtifactUtils.componentHint("Kafka")).isEqualTo(" (not a built-in Camel component; did you mean 'kafka'?)");
        assertThat(ArtifactUtils.componentHint("cheese")).isEqualTo(" (not a built-in Camel component)");
        assertThat(ArtifactUtils.componentHint("")).isEmpty();
        assertThat(ArtifactUtils.componentHint(null)).isEmpty();

        assertThat(ArtifactUtils.languageHint("xquery")).startsWith(" (the xquery language is in camel-saxon; add camel-saxon");
        assertThat(ArtifactUtils.languageHint("simpel")).isEqualTo(" (not a built-in Camel language; did you mean 'simple'?)");
        assertThat(ArtifactUtils.languageHint("simple")).startsWith(" (the simple language is in camel-core-languages;");

        assertThat(ArtifactUtils.dataFormatHint("jaxb")).startsWith(" (the jaxb data format is in camel-jaxb; add camel-jaxb");
        assertThat(ArtifactUtils.dataFormatHint("jakson"))
                .isEqualTo(" (not a built-in Camel data format; did you mean 'jackson'?)");
    }

    @Test
    public void testClosest() {
        List<String> names = List.of("kafka", "kamelet", "file", "ftp", "sftp", "xj", "xslt");
        assertThat(ArtifactUtils.closest("kafak", names)).isEqualTo("kafka");
        assertThat(ArtifactUtils.closest("KAFKA", names)).isEqualTo("kafka");
        assertThat(ArtifactUtils.closest("files", names)).isEqualTo("file");
        // short names allow one edit only, so xxx is nothing
        assertThat(ArtifactUtils.closest("xxx", names)).isNull();
        assertThat(ArtifactUtils.closest("ftps", names)).isEqualTo("ftp");
        assertThat(ArtifactUtils.closest("cheese", names)).isNull();
        assertThat(ArtifactUtils.closest("", names)).isNull();
        assertThat(ArtifactUtils.closest("kafka", null)).isNull();
    }

    @Test
    public void testClosestTie() {
        List<String> names = List.of("ftp", "ftps", "http", "https", "jms", "jmx");
        // same distance to ftps and https: the longer shared prefix wins, as typos are seldom in the first letters
        assertThat(ArtifactUtils.closest("htps", names)).isEqualTo("https");
        assertThat(ArtifactUtils.closest("htp", names)).isEqualTo("http");
        // same distance and same prefix to jms and jmx: no guess rather than a wrong one
        assertThat(ArtifactUtils.closest("jm", names)).isNull();
        // the real table, which has all of these
        assertThat(ArtifactUtils.componentHint("htps")).isEqualTo(" (not a built-in Camel component; did you mean 'https'?)");
        // null candidates are skipped
        assertThat(ArtifactUtils.closest("kafak", Arrays.asList(null, "kafka"))).isEqualTo("kafka");
    }

    @Test
    public void testDistance() {
        assertThat(ArtifactUtils.distance("kafka", "kafka")).isZero();
        assertThat(ArtifactUtils.distance("kafak", "kafka")).isEqualTo(1);
        assertThat(ArtifactUtils.distance("", "abc")).isEqualTo(3);
        assertThat(ArtifactUtils.distance("abc", "")).isEqualTo(3);
        assertThat(ArtifactUtils.distance("simple", "sample")).isEqualTo(1);
    }
}
