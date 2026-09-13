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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.commands.ai.SourceValidator.BeanDeclarations;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.looksLikeProse;

/**
 * The Java source checks of {@link SourceValidator}: compile errors with hints, a null branch dereference, a class
 * named like its interface, a message method called on the Exchange.
 */
final class JavaChecks {

    private JavaChecks() {
    }

    /**
     * Compiles the Java source in memory with the JDK compiler against the classpath of the running CLI, so a bean or
     * processor that would fail when {@code camel run} compiles it is reported first, one message per diagnostic with
     * its line. Returns nothing when no compiler is available (a JRE).
     */
    public static List<String> validateJava(String fileName, String content) {
        List<String> msgs = new ArrayList<>();
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null || content == null || content.isBlank()) {
            return msgs;
        }
        String simple = fileName.substring(fileName.lastIndexOf('/') + 1);
        simple = simple.substring(simple.lastIndexOf('\\') + 1);
        String className = simple.substring(0, simple.length() - ".java".length());
        Matcher pm = JAVA_PACKAGE_PATTERN.matcher(content);
        String pkg = pm.find() ? pm.group(1) : null;
        java.net.URI uri
                = java.net.URI.create("string:///" + (pkg != null ? pkg.replace('.', '/') + "/" : "") + className + ".java");
        javax.tools.JavaFileObject source = new javax.tools.SimpleJavaFileObject(uri, javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new javax.tools.DiagnosticCollector<>();
        try {
            Path out = Files.createTempDirectory("camel-validate-java");
            try {
                List<String> options = new ArrayList<>(
                        List.of("-proc:none", "-Xlint:none", "-d", out.toString(),
                                "-classpath", System.getProperty("java.class.path", "")));
                compiler.getTask(null, null, diagnostics, options, null, List.of(source)).call();
            } finally {
                try (var walk = Files.walk(out)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
                }
            }
        } catch (Exception e) {
            return msgs;
        }
        String[] lines = content.split("\n", -1);
        for (var d : diagnostics.getDiagnostics()) {
            if (d.getKind() == javax.tools.Diagnostic.Kind.ERROR) {
                String text = d.getMessage(Locale.ENGLISH).replace("\n", " ").trim();
                long ln = d.getLineNumber();
                if ((text.contains("expected") || text.startsWith("illegal character"))
                        && ln >= 1 && ln <= lines.length && looksLikeProse(lines[(int) ln - 1])) {
                    // an explanation appended after the class: the most common way a model breaks a Java file
                    String t = lines[(int) ln - 1].trim();
                    msgs.add("Line " + ln + " is not Java (\"" + (t.length() > 40 ? t.substring(0, 40) + "..." : t)
                             + "\"): a Java file holds only the class; put explanations in a // comment or leave them out");
                    return msgs;
                }
                if (text.contains("is already defined in this compilation unit")) {
                    // the class is named like the interface it imports (class AggregationStrategy implements
                    // AggregationStrategy)
                    String clash = text.substring(0, text.indexOf(" is already defined")).trim();
                    text += " (the class is named " + clash + ", like the type it imports: rename the class, for example"
                            + " My" + clash + ", and use that name in the route's beans)";
                }
                msgs.add("Line " + ln + ": " + text);
            }
        }
        if (msgs.isEmpty()) {
            msgs.addAll(nullBranchDereference(content));
        }
        for (int i = 0; i < msgs.size(); i++) {
            String m = msgs.get(i);
            Matcher mm = MISSING_METHOD_ON_EXCHANGE_PATTERN.matcher(m);
            if (mm.find()) {
                // exchange.setHeader(...): the body and the headers live on the message, not on the exchange
                String method = mm.group(1);
                String where = mm.group(2).contains("Exchange") ? "Exchange" : "Message";
                msgs.set(i, m + " (" + where + " has no " + method + ": the body, headers and variables are on the message:"
                            + " exchange.getMessage()." + method + "(...), and exchange.getMessage().getBody(String.class))");
            }
        }
        if (!msgs.isEmpty()) {
            String first = msgs.get(0);
            if (first.contains("cannot find symbol") && !first.contains("symbol:   method")
                    || first.contains("package") && first.contains("does not exist")) {
                msgs.set(0, first + " (the class is compiled when camel run starts against the CLI classpath: Camel and"
                            + " the JDK; another library needs a //DEPS group:artifact:version line at the top of"
                            + " the Java file, or the dependency added to the run)");
            }
        }
        return msgs;
    }

    static final Pattern MISSING_METHOD_ON_EXCHANGE_PATTERN = Pattern.compile(
            "cannot find symbol\\s+symbol:\\s+method ((?:get|set)(?:Body|Header|Headers|Variable|Variables)[A-Za-z]*)\\([^)]*\\)\\s+location:.*type (org\\.apache\\.camel\\.Exchange)");

    static final Pattern NULL_BRANCH_PATTERN
            = Pattern.compile("if\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*==\\s*null\\s*\\)\\s*\\{");

    /**
     * A variable used inside its own {@code if (x == null) {} } block: the first call of an AggregationStrategy has
     * oldExchange == null, and a model that tests for that and then calls oldExchange.getIn() in the branch gets a
     * NullPointerException on the first message. The compiler accepts it, so name it here.
     */
    static List<String> nullBranchDereference(String content) {
        List<String> msgs = new ArrayList<>();
        Matcher m = NULL_BRANCH_PATTERN.matcher(content);
        while (m.find()) {
            String var = m.group(1);
            int depth = 1;
            int i = m.end();
            for (; i < content.length() && depth > 0; i++) {
                char ch = content.charAt(i);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                }
            }
            String block = content.substring(m.end(), i);
            Matcher use = Pattern.compile("(?<![A-Za-z0-9_.])" + Pattern.quote(var) + "\\s*\\.").matcher(block);
            Matcher assign = Pattern.compile("(?<![A-Za-z0-9_.])" + Pattern.quote(var) + "\\s*=[^=]").matcher(block);
            if (use.find() && !(assign.find() && assign.start() < use.start())) {
                int line = 1 + (int) content.substring(0, m.end() + use.start()).chars().filter(c -> c == '\n').count();
                String hint = "oldExchange".equals(var)
                        ? " (the first message of an aggregation has no oldExchange yet: return newExchange in this"
                          + " branch, and merge into oldExchange in the other)"
                        : "";
                msgs.add("Line " + line + ": " + var + " is null inside if (" + var + " == null), so " + var
                         + ". throws NullPointerException" + hint);
            }
        }
        return msgs;
    }

    static final Pattern MISSING_CLASS_PATTERN = Pattern.compile("cannot find symbol\\s+symbol:\\s+class (\\w+)");

    static final Pattern MISSING_PACKAGE_PATTERN = Pattern.compile("package ([\\w.]+) does not exist");

    /**
     * "cannot find symbol: class MemoryLeakSimulator" when MemoryLeakSimulator.java is next to the route: the import
     * names a package the class is not in. Say what the sibling's package is (or that it has none) instead of the
     * dependency hint.
     */
    static List<String> withSiblingClassHints(List<String> msgs, BeanDeclarations siblings) {
        List<String> answer = new ArrayList<>(msgs.size());
        for (String msg : msgs) {
            Matcher m = MISSING_CLASS_PATTERN.matcher(msg);
            String cls = m.find() ? m.group(1) : null;
            if (cls == null) {
                Matcher pm = MISSING_PACKAGE_PATTERN.matcher(msg);
                if (pm.find()) {
                    String pkg = pm.group(1);
                    cls = pkg.substring(pkg.lastIndexOf('.') + 1);
                }
            }
            String fqcn = cls != null ? siblings.javaClasses().get(cls.toLowerCase(Locale.ROOT)) : null;
            if (fqcn != null) {
                int cut = msg.indexOf(" (the class is compiled");
                String head = cut > 0 ? msg.substring(0, cut) : msg;
                String pkg = fqcn.contains(".") ? fqcn.substring(0, fqcn.lastIndexOf('.')) : null;
                answer.add(head + " (" + cls + " is the class in " + cls + ".java next to this file"
                           + (pkg != null
                                   ? ", in package " + pkg + ": import " + fqcn
                                   : ", which has no package: it is in the default package, drop the import and use "
                                     + cls + " directly, or give both files the same package")
                           + ")");
            } else {
                answer.add(msg);
            }
        }
        return answer;
    }

    static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("\\b(?:public\\s+)?(?:final\\s+)?class\\s+(\\w+)");

    static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    static final Pattern PUBLIC_METHOD_PATTERN = Pattern.compile(
            "^\\s*public\\s+(?!class\\b|interface\\b|enum\\b|record\\b)(?:static\\s+)?[\\w<>\\[\\],. ?]+?\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE);

    /** The public methods a class declares (constructors and main excluded): a POJO aggregation strategy needs one. */
    static int countPublicMethods(String src, String simpleClassName) {
        return publicMethodNames(src, simpleClassName).size();
    }

    static List<String> publicMethodNames(String src, String simpleClassName) {
        List<String> names = new ArrayList<>();
        Matcher m = PUBLIC_METHOD_PATTERN.matcher(src);
        while (m.find()) {
            String name = m.group(1);
            if (!name.equals(simpleClassName) && !name.equals("main") && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

}
