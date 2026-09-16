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
package org.apache.camel.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every {@code import org.apache.camel...} line of a Java example in the documentation names a class of the source tree
 * (CAMEL-24774): an import of a class that moved to another package, or never was in the one written, fails the build
 * here instead of in the reader's IDE. The examples of the catalog pages and of the user manual are checked when the
 * tests run inside the Camel repository, where the source tree is.
 */
class DocExamplesJavaImportsTest {

    private static final Pattern JAVA_BLOCK = Pattern.compile("\\[source,java\\]\\n-{4,}\\n(.*?)\\n-{4,}", Pattern.DOTALL);
    private static final Pattern IMPORT
            = Pattern.compile("^\\s*import\\s+(static\\s+)?(org\\.apache\\.camel\\.[\\w.]+?)(\\.\\*)?\\s*;", Pattern.MULTILINE);

    private static CamelCatalog catalog;
    private static Path root;
    private static Set<String> classes;

    private record DocExamples(int imports, List<String> failures) {
    }

    @BeforeAll
    static void setup() throws Exception {
        catalog = new DefaultCamelCatalog();
        root = UserManualPages.repositoryRoot();
        if (root != null) {
            classes = classesOfTheSourceTree(root);
        }
    }

    @Test
    void everyImportOfTheDocumentationExamplesResolves() throws Exception {
        assumeTrue(root != null, "the imports are only checked inside the Camel source tree");
        Map<String, String> pages = new LinkedHashMap<>();
        for (String page : catalog.findDocNames()) {
            String doc = catalog.asciiDoc(page);
            if (doc != null) {
                pages.put(page, doc);
            }
        }

        DocExamples result = check(pages);

        assertTrue(result.imports() > 50, "imports found in the documentation examples: " + result.imports());
        assertTrue(result.failures().isEmpty(),
                "Documentation examples importing a class that is not there:\n  " + String.join("\n  ", result.failures()));
    }

    @Test
    void everyImportOfTheUserManualExamplesResolves() throws Exception {
        assumeTrue(root != null, "the imports are only checked inside the Camel source tree");

        DocExamples result = check(UserManualPages.currentPages());

        assertTrue(result.imports() > 20, "imports found in the user manual examples: " + result.imports());
        assertTrue(result.failures().isEmpty(),
                "User manual examples importing a class that is not there:\n  " + String.join("\n  ", result.failures()));
    }

    @Test
    void theCheckSeesWhatItIsFor() {
        assumeTrue(root != null, "the imports are only checked inside the Camel source tree");
        String page = "[source,java]\n----\n"
                      + "import org.apache.camel.CamelContext;\n"
                      + "import org.apache.camel.builder.RouteBuilder;\n"
                      + "import org.apache.camel.model.OnExceptionDefinition.RedeliveryOption;\n"
                      + "import org.apache.camel.support.builder.*;\n"
                      + "import static org.apache.camel.builder.PredicateBuilder.not;\n"
                      + "import org.apache.camel.util.jsse.SSLContextParameters;\n"
                      + "import org.apache.camel.impl.DefaultProducer;\n"
                      + "----\n";

        DocExamples result = check(Map.of("fake", page));

        assertTrue(result.imports() == 7, "imports seen: " + result.imports());
        assertTrue(result.failures().size() == 2 && result.failures().get(0).contains("org.apache.camel.util.jsse")
                && result.failures().get(1).contains("org.apache.camel.impl.DefaultProducer"),
                "the two classes that moved, not the nested class, the static member or the wildcard: " + result.failures());
    }

    private static DocExamples check(Map<String, String> pages) {
        int imports = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> entry : pages.entrySet()) {
            String page = entry.getKey();
            String doc = entry.getValue();
            Matcher m = JAVA_BLOCK.matcher(doc);
            while (m.find()) {
                String java = m.group(1);
                Matcher im = IMPORT.matcher(java);
                while (im.find()) {
                    imports++;
                    String name = im.group(2);
                    boolean wildcard = im.group(3) != null;
                    if (!resolves(name, im.group(1) != null, wildcard)) {
                        int line = 1 + countLines(doc, m.start(1)) + countLines(java, im.start());
                        failures.add(page + ".adoc:" + line + ": import " + name + (wildcard ? ".*" : ""));
                    }
                }
            }
        }
        return new DocExamples(imports, failures);
    }

    /**
     * Whether the imported name is a class of the source tree, a nested class of one, a static member of one, or a
     * package or class a wildcard import refers to.
     */
    private static boolean resolves(String name, boolean isStatic, boolean wildcard) {
        if (wildcard) {
            if (classes.contains(name)) {
                return true;
            }
            String prefix = name + ".";
            return classes.stream().anyMatch(c -> c.startsWith(prefix));
        }
        // the name itself, then the outer classes of a nested class or the class of a static member
        String candidate = name;
        while (true) {
            if (classes.contains(candidate)) {
                return true;
            }
            int dot = candidate.lastIndexOf('.');
            if (dot < 0) {
                return false;
            }
            String last = candidate.substring(dot + 1);
            candidate = candidate.substring(0, dot);
            // a lower-case segment is a package, not a class that could nest the rest
            if (!isStatic && !Character.isUpperCase(last.charAt(0))) {
                return false;
            }
        }
    }

    /** The fully qualified names of the classes of the source tree, from src/main/java and src/generated/java. */
    private static Set<String> classesOfTheSourceTree(Path root) throws IOException {
        Set<String> answer = new HashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(f -> f.toString().endsWith(".java"))
                    .map(Path::toString)
                    .forEach(f -> {
                        int i = f.indexOf("/src/main/java/org/apache/camel/");
                        if (i < 0) {
                            i = f.indexOf("/src/generated/java/org/apache/camel/");
                        }
                        if (i >= 0 && !f.contains("/target/")) {
                            String rel = f.substring(f.indexOf("/org/apache/camel/", i) + 1, f.length() - 5);
                            answer.add(rel.replace('/', '.'));
                        }
                    });
        }
        return answer;
    }

    private static int countLines(String text, int end) {
        int n = 0;
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
