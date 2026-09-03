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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceViewerRefactorTest {

    // ---- extractUriFromLine ----

    @Test
    void extractUriInlineToWithQueryParams() {
        assertThat(SourceViewer.extractUriFromLine("    - to: timer:tick?period=1000"))
                .isEqualTo("timer:tick");
    }

    @Test
    void extractUriInlineFrom() {
        assertThat(SourceViewer.extractUriFromLine("  - from: timer:tick"))
                .isEqualTo("timer:tick");
    }

    @Test
    void extractUriBlockUriLine() {
        assertThat(SourceViewer.extractUriFromLine("    uri: log:out?showAll=true"))
                .isEqualTo("log:out");
    }

    @Test
    void extractUriRouteFromNoDash() {
        // route-level "from:" without a leading dash (inside "- route:")
        assertThat(SourceViewer.extractUriFromLine("    from: timer:tick?period=1000"))
                .isEqualTo("timer:tick");
    }

    @Test
    void extractUriInlineToD() {
        assertThat(SourceViewer.extractUriFromLine("    - toD: ${header.target}"))
                .isEqualTo("${header.target}");
    }

    @Test
    void extractUriQuoted() {
        assertThat(SourceViewer.extractUriFromLine("    - to: \"http://example.com/path?q=1\""))
                .isEqualTo("http://example.com/path");
    }

    @Test
    void extractUriNotAUriLine() {
        assertThat(SourceViewer.extractUriFromLine("    constant: Hello World")).isNull();
    }

    @Test
    void extractUriEmptyBlock() {
        assertThat(SourceViewer.extractUriFromLine("    - to:")).isNull();
    }

    @Test
    void extractUriNull() {
        assertThat(SourceViewer.extractUriFromLine(null)).isNull();
    }

    // ---- replaceUriOnLine ----

    @Test
    void replaceUriInlineTo() {
        assertThat(SourceViewer.replaceUriOnLine("    - to: timer:tick?period=1000", "log:out"))
                .isEqualTo("    - to: log:out");
    }

    @Test
    void replaceUriInlineFrom() {
        assertThat(SourceViewer.replaceUriOnLine("  - from: timer:tick", "direct:start"))
                .isEqualTo("  - from: direct:start");
    }

    @Test
    void replaceUriBlockUri() {
        assertThat(SourceViewer.replaceUriOnLine("      uri: log:out?showAll=true", "kafka:my-topic"))
                .isEqualTo("      uri: kafka:my-topic");
    }

    @Test
    void replaceUriPreservesIndent() {
        String line = "        - to: mock:result";
        assertThat(SourceViewer.replaceUriOnLine(line, "log:replaced"))
                .isEqualTo("        - to: log:replaced");
    }

    // ---- extractValueFromLine ----

    @Test
    void extractValuePlainString() {
        assertThat(SourceViewer.extractValueFromLine("    constant: Hello World"))
                .isEqualTo("Hello World");
    }

    @Test
    void extractValueQuoted() {
        assertThat(SourceViewer.extractValueFromLine("    message: \"some text\""))
                .isEqualTo("some text");
    }

    @Test
    void extractValueSingleQuoted() {
        assertThat(SourceViewer.extractValueFromLine("    constant: 'fixed text'"))
                .isEqualTo("fixed text");
    }

    @Test
    void extractValueAlreadyPlaceholder() {
        assertThat(SourceViewer.extractValueFromLine("    constant: {{my.key}}")).isNull();
    }

    @Test
    void extractValueAlreadyPlaceholderQuoted() {
        assertThat(SourceViewer.extractValueFromLine("    expression: \"{{greeting.message}}\"")).isNull();
    }

    @Test
    void extractValueListItem() {
        assertThat(SourceViewer.extractValueFromLine("    - to: timer:tick")).isNull();
    }

    @Test
    void extractValueEmptyValue() {
        assertThat(SourceViewer.extractValueFromLine("    steps:")).isNull();
    }

    @Test
    void extractValueYamlMap() {
        assertThat(SourceViewer.extractValueFromLine("    parameters: {period: 1000}")).isNull();
    }

    // ---- replaceValueWithPlaceholder ----

    @Test
    void replaceValueSimple() {
        assertThat(SourceViewer.replaceValueWithPlaceholder("    constant: Hello World", "greeting.message"))
                .isEqualTo("    constant: \"{{greeting.message}}\"");
    }

    @Test
    void replaceValuePreservesIndent() {
        assertThat(SourceViewer.replaceValueWithPlaceholder("      message: some text", "my.msg"))
                .isEqualTo("      message: \"{{my.msg}}\"");
    }

    @Test
    void replaceValueQuotedOriginal() {
        assertThat(SourceViewer.replaceValueWithPlaceholder("    constant: \"Hello\"", "my.key"))
                .isEqualTo("    constant: \"{{my.key}}\"");
    }

    // ---- removeParametersBlock ----

    private static List<String> lines(String... ls) {
        return new ArrayList<>(Arrays.asList(ls));
    }

    @Test
    void removeParametersBlockBasic() {
        List<String> input = lines(
                "    - from:",
                "        uri: timer:tick",
                "        parameters:",
                "          period: \"1000\"",
                "          fixedRate: true",
                "    - to: log:out");
        int uriRow = 1;
        SourceViewer.removeParametersBlock(input, uriRow, input.get(uriRow));
        assertThat(input).containsExactly(
                "    - from:",
                "        uri: timer:tick",
                "    - to: log:out");
    }

    @Test
    void removeParametersBlockSingleParam() {
        List<String> input = lines(
                "        uri: log:out",
                "        parameters:",
                "          showAll: true");
        SourceViewer.removeParametersBlock(input, 0, input.get(0));
        assertThat(input).containsExactly("        uri: log:out");
    }

    @Test
    void removeParametersBlockNotPresentSkips() {
        List<String> input = lines(
                "        uri: log:out",
                "        id: my-step");
        SourceViewer.removeParametersBlock(input, 0, input.get(0));
        assertThat(input).containsExactly(
                "        uri: log:out",
                "        id: my-step");
    }

    @Test
    void removeParametersBlockInlineLineSkips() {
        // Only block-form "uri:" lines trigger removal; inline "- to:" should be a no-op
        List<String> input = lines(
                "    - to: timer:tick?period=1000",
                "    - log: \"done\"");
        SourceViewer.removeParametersBlock(input, 0, input.get(0));
        assertThat(input).containsExactly(
                "    - to: timer:tick?period=1000",
                "    - log: \"done\"");
    }

    @Test
    void removeParametersBlockNullLineSkips() {
        List<String> input = lines("        uri: log:out");
        SourceViewer.removeParametersBlock(input, 0, null);
        assertThat(input).containsExactly("        uri: log:out");
    }

    // ---- isExtractableStep ----

    @Test
    void isExtractableStepSetBody() {
        assertThat(SourceViewer.isExtractableStep("      - setBody:")).isTrue();
    }

    @Test
    void isExtractableStepChoice() {
        assertThat(SourceViewer.isExtractableStep("    - choice:")).isTrue();
    }

    @Test
    void isExtractableStepToIsExtractable() {
        // "- to:" steps can be extracted (wrapped in a new route)
        assertThat(SourceViewer.isExtractableStep("      - to: log:out")).isTrue();
    }

    @Test
    void isExtractableStepRouteExcluded() {
        assertThat(SourceViewer.isExtractableStep("- route:")).isFalse();
    }

    @Test
    void isExtractableStepFromExcluded() {
        assertThat(SourceViewer.isExtractableStep("  - from: timer:tick")).isFalse();
    }

    @Test
    void isExtractableStepNullFalse() {
        assertThat(SourceViewer.isExtractableStep(null)).isFalse();
    }

    @Test
    void isExtractableStepNonListItem() {
        assertThat(SourceViewer.isExtractableStep("    steps:")).isFalse();
    }

    // ---- buildExtractedRouteYaml ----

    @Test
    void buildExtractedRouteYamlWrapsBlock() {
        List<String> block = lines(
                "      - setBody:",
                "          expression:",
                "            constant: Hello");
        String result = SourceViewer.buildExtractedRouteYaml("my-sub", block, 6);
        assertThat(result).isEqualTo(
                "- route:\n" +
                                     "    from:\n" +
                                     "      uri: direct:my-sub\n" +
                                     "    steps:\n" +
                                     "      - setBody:\n" +
                                     "          expression:\n" +
                                     "            constant: Hello\n");
    }

    @Test
    void buildExtractedRouteYamlPreservesChildIndent() {
        List<String> block = lines(
                "      - choice:",
                "          when:",
                "            - simple: \"${body} != null\"",
                "              steps:",
                "                - to: log:info");
        String result = SourceViewer.buildExtractedRouteYaml("check-body", block, 6);
        assertThat(result).startsWith("- route:\n    from:\n      uri: direct:check-body\n    steps:\n");
        assertThat(result).contains("      - choice:\n");
        assertThat(result).contains("          when:\n");
    }

    // ---- sanitizeFileName ----

    @Test
    void sanitizeFileNamePlain() {
        assertThat(SourceViewer.sanitizeFileName("my-sub-route")).isEqualTo("my-sub-route");
    }

    @Test
    void sanitizeFileNameSpacesReplaced() {
        assertThat(SourceViewer.sanitizeFileName("my sub route")).isEqualTo("my-sub-route");
    }

    @Test
    void sanitizeFileNameSpecialCharsReplaced() {
        assertThat(SourceViewer.sanitizeFileName("hello world!@#")).isEqualTo("hello-world");
    }

    @Test
    void sanitizeFileNameColonsAndSlashesReplaced() {
        assertThat(SourceViewer.sanitizeFileName("timer:tick/sub")).isEqualTo("timer-tick-sub");
    }

    @Test
    void sanitizeFileNameConsecutiveHyphensCollapsed() {
        assertThat(SourceViewer.sanitizeFileName("a  b")).isEqualTo("a-b");
    }

    @Test
    void sanitizeFileNameLeadingTrailingHyphensStripped() {
        assertThat(SourceViewer.sanitizeFileName("  -my-route-  ")).isEqualTo("my-route");
    }

    @Test
    void sanitizeFileNameNullEmpty() {
        assertThat(SourceViewer.sanitizeFileName(null)).isEmpty();
        assertThat(SourceViewer.sanitizeFileName("   ")).isEmpty();
    }
}
