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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixes of the problems the validation reports, applied to their line, in the three DSLs.
 */
class QuickFixesTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    /** The line of the only problem of the source, with its fix applied. */
    private static String fixed(String file, String content) {
        List<String> errors = SourceValidator.validate(file, content, catalog, null, null);
        assertThat(errors).as(file).hasSize(1);
        String error = errors.get(0);
        int line = Integer.parseInt(error.substring(5, error.indexOf(':')));
        String text = content.split("\n", -1)[line - 1];
        QuickFixes.Fix fix = QuickFixes.fixFor(error, text);
        assertThat(fix).as(error).isNotNull();
        return fix.apply(text).strip();
    }

    private static String java(String steps) {
        return """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() throws Exception {
                %s
                    }
                }
                """.formatted(steps);
    }

    @Test
    void aTypoOfAnOption() {
        assertThat(fixed("MyRoute.java", java("        from(\"timer:tick?peroid=1000\").to(\"seda:out\");")))
                .isEqualTo("from(\"timer:tick?period=1000\").to(\"seda:out\");");
        assertThat(fixed("routes.xml", """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick?peroid=1000"/>
                        <to uri="seda:out"/>
                    </route>
                </routes>
                """)).isEqualTo("<from uri=\"timer:tick?period=1000\"/>");
    }

    @Test
    void aTypoOfAYamlOption() {
        assertThat(fixed("route.camel.yaml", """
                - route:
                    from:
                      uri: "timer:tick"
                      steps:
                        - log:
                            message: "${body}"
                            logLevel: WARN
                """)).isEqualTo("loggingLevel: WARN");
    }

    @Test
    void anEnumValueALetterOff() {
        assertThat(fixed("MyRoute.java", java("        from(\"timer:tick\").to(\"file:out?fileExist=Overide\");")))
                .isEqualTo("from(\"timer:tick\").to(\"file:out?fileExist=Override\");");
    }

    @Test
    void aBooleanValue() {
        assertThat(fixed("MyRoute.java", java("        from(\"timer:tick\").to(\"log:x?showAll=tru\");")))
                .isEqualTo("from(\"timer:tick\").to(\"log:x?showAll=true\");");
    }

    @Test
    void aToThatNeedsToD() {
        assertThat(fixed("MyRoute.java", java("        from(\"timer:tick\")\n            .to(\"seda:${header.q}\");")))
                .isEqualTo(".toD(\"seda:${header.q}\");");
        assertThat(fixed("routes.xml", """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick"/>
                        <to uri="seda:${header.q}"/>
                    </route>
                </routes>
                """)).isEqualTo("<toD uri=\"seda:${header.q}\"/>");
    }

    @Test
    void aSimpleExpressionWhereAPlaceholderIsMeant() {
        assertThat(fixed("MyRoute.java", java("        from(\"timer:tick?period=${welcome.period}\").to(\"seda:out\");")))
                .isEqualTo("from(\"timer:tick?period={{welcome.period}}\").to(\"seda:out\");");
    }

    @Test
    void theValidateToolGivesTheFixesAsEdits() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put("file", "MyRoute.java");
        args.put("content", java("        from(\"timer:tick?peroid=1000\").to(\"seda:out\");"));
        JsonObject result = (JsonObject) Jsoner
                .deserialize(String.valueOf(ToolRegistry.execute("camel_validate_source", new ToolContext(), args)));
        JsonArray fixes = (JsonArray) result.get("fixes");
        assertThat(fixes).hasSize(1);
        JsonObject fix = (JsonObject) fixes.get(0);
        assertThat(fix.getInteger("line")).isEqualTo(6);
        assertThat(fix.getString("find")).isEqualTo("peroid=");
        assertThat(fix.getString("replace")).isEqualTo("period=");
    }

    @Test
    void noFixWhenItIsNotCertain() {
        // two choices as close, a value nothing like any, a line without the text
        assertThat(QuickFixes.closest("ab", List.of("ac", "ad"))).isNull();
        assertThat(QuickFixes.closest("Bogus", List.of("Override", "Append", "Fail"))).isNull();
        assertThat(QuickFixes.fixFor("timer: Unknown option 'peroid'. Did you mean: [period]", "nothing here"))
                .isNull();
        assertThat(QuickFixes.fixFor("Simple syntax error: Unexpected token", "simple(\"x\")")).isNull();
    }
}
