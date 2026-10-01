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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.java.in.LwJavaParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25182: every Java route example of the documentation calls the Java DSL as it is. The examples are read with
 * the Java DSL parser of camel-java-io, which replays each chain of calls against Camel's DSL without compiling the
 * snippet. What the parser cannot see (lambdas, variables, helper methods) is fine; a DSL method that does not exist,
 * or does not take the arguments of the example, is a broken example.
 */
class CatalogDocJavaExamplesTest {

    private static final Pattern BLOCK
            = Pattern.compile("(?:^\\.(?<title>[^\\n]*)\\n)?\\[source,java\\]\\n-{4,}\\n(?<code>.*?)\\n-{4,}",
                    Pattern.DOTALL | Pattern.MULTILINE);

    /** A snippet with routes: blocks of plain Java (beans, configuration) are not read. */
    private static final Pattern ROUTE_DSL = Pattern.compile(
            "(?<![\\w.])(from|fromF|rest|routeTemplate|onException|errorHandler|interceptFrom|intercept"
                                                             + "|interceptSendToEndpoint|onCompletion)\\s*\\(");

    /** Titles of examples that show code that is wrong on purpose, or is not Camel's Java DSL. */
    private static final List<String> TITLES_SKIPPED
            = List.of("pseudo", "incorrect", "will not compile", "does not compile", "before migration", "old ");

    /** The reasons of the parser that mean the example calls the DSL wrongly. */
    private static final List<String> BROKEN = List.of(
            "no such DSL method", "arguments the DSL method does not take", "the DSL refused it");

    /** Where the user manual is in the source tree; its pages are not in the catalog. */
    private static final Path USER_MANUAL = Path.of("../../../docs/user-manual/modules/ROOT/pages");

    /** User manual pages that show old syntax on purpose. */
    private static final List<String> USER_MANUAL_SKIPPED = List.of("upgrade-guide", "migration-guide");

    private static CamelCatalog catalog;

    @BeforeAll
    static void setup() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    void everyJavaExampleOfTheDocumentationCallsTheJavaDsl() throws IOException {
        List<String> failures = new ArrayList<>();
        int examples = 0;
        for (String name : catalog.findDocNames()) {
            examples += check(name, catalog.asciiDoc(name), failures);
        }
        if (Files.isDirectory(USER_MANUAL)) {
            try (Stream<Path> files = Files.list(USER_MANUAL)) {
                for (Path f : files.filter(f -> f.toString().endsWith(".adoc")).sorted().toList()) {
                    String name = f.getFileName().toString().replace(".adoc", "");
                    if (USER_MANUAL_SKIPPED.stream().noneMatch(name::contains)) {
                        examples += check(name, Files.readString(f), failures);
                    }
                }
            }
        }
        assertThat(examples).as("Java route examples found in the documentation").isGreaterThan(2000);
        assertThat(failures).as("Documentation examples that call the Java DSL wrongly").isEmpty();
    }

    private static int check(String page, String doc, List<String> failures) {
        if (doc == null) {
            return 0;
        }
        int examples = 0;
        Matcher m = BLOCK.matcher(doc);
        while (m.find()) {
            String title = m.group("title") != null ? m.group("title").toLowerCase(Locale.ROOT) : "";
            String code = m.group("code");
            if (!ROUTE_DSL.matcher(code).find() || TITLES_SKIPPED.stream().anyMatch(title::contains)) {
                continue;
            }
            examples++;
            int line = 1 + (int) doc.substring(0, m.start("code")).chars().filter(c -> c == '\n').count();
            JavaParseResult result = new LwJavaParser()
                    .setEndpointDslResolver(new CatalogEndpointDslResolver(catalog))
                    .setConstantResolver(new ProjectConstantResolver(Map.of(), catalog))
                    .parse(code);
            for (JavaParseResult.Unresolved u : result.unresolved()) {
                if (BROKEN.stream().anyMatch(u.reason()::startsWith)) {
                    failures.add(page + ":" + (line + u.line() - 1) + " " + u.text() + " - " + u.reason());
                }
            }
        }
        return examples;
    }
}
