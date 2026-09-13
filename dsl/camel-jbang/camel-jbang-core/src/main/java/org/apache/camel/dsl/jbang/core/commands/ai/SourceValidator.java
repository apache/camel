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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.networknt.schema.Error;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.dsl.yaml.validator.YamlValidator;

/**
 * Validates integration source files the way the Camel TUI's editor does on save, before an AI agent writes them: Camel
 * YAML DSL against the YAML DSL schema (unknown or misspelled options, wrong structure), then the endpoint URIs and
 * simple expressions in it against the catalog; a .properties file line by line against the catalog of {@code camel.*}
 * options. Every message names the line so the agent can fix the file.
 */
public final class SourceValidator {

    private static final Set<String> PREDICATE_EIPS = Set.of(
            "filter", "when", "validate", "onWhen", "on-when",
            "handled", "continued", "retryWhile", "retry-while",
            "completionPredicate", "completion-predicate",
            "completion", "loopDoWhile", "loop-do-while");

    private static final Set<String> CONSUMER_EIPS
            = Set.of("from", "pollEnrich", "poll-enrich", "poll", "interceptFrom", "intercept-from");
    private static final Set<String> PRODUCER_EIPS
            = Set.of("to", "toD", "to-d", "wireTap", "wire-tap", "enrich",
                    "interceptSendToEndpoint", "intercept-send-to-endpoint");

    private static final Pattern YAML_URI_PATTERN = Pattern.compile(
            "^\\s*-?\\s*(?:uri|from|to|toD|wireTap|enrich|pollEnrich|deadLetterChannel):\\s*\"?([a-zA-Z][a-zA-Z0-9+.-]*(?::[^\"\\s]*)?)");

    private static volatile YamlValidator yamlValidator;

    private SourceValidator() {
    }

    /** Whether the file has a validator: YAML routes and .properties files do, other files have none. */
    public static boolean isValidatableFile(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".properties")
                || name.endsWith(".java") || name.endsWith(".xsl") || name.endsWith(".xslt") || name.endsWith(".xml");
    }

    /**
     * Validates source by file type: Camel YAML DSL for .yaml/.yml files, Camel options for .properties files. Other
     * file types have no validation and yield no messages.
     *
     * @param  fileName          the file name; its extension picks the checks
     * @param  content           the source
     * @param  catalog           the catalog of the Camel version the source is for
     * @param  extraPropertyLine an extra check for a properties line the catalog does not know (Spring Boot
     *                           properties), returning the message or null; may be null
     * @return                   the messages, empty when the source is valid
     */
    public static List<String> validate(
            String fileName, String content, CamelCatalog catalog, Function<String, String> extraPropertyLine) {
        return validate(fileName, content, catalog, extraPropertyLine, null);
    }

    /**
     * As {@link #validate(String, String, CamelCatalog, Function)}, and when the directory is given also checks that
     * every bean the YAML refers to is declared: in the file, in a sibling YAML file, or by a Java class in the
     * directory annotated with {@code @BindToRegistry} (CAMEL-24698).
     */
    public static List<String> validate(
            String fileName, String content, CamelCatalog catalog, Function<String, String> extraPropertyLine,
            Path directory) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            List<String> msgs = validateCamelYaml(content, catalog);
            if (directory != null && msgs.isEmpty()) {
                msgs = new ArrayList<>(msgs);
                msgs.addAll(validateYamlBeanRefs(content, BeanDeclarations.scan(directory, fileName), catalog));
                msgs.addAll(validateResourceRefs(content, directory));
            }
            return msgs;
        }
        if (name.endsWith(".properties")) {
            return validateProperties(content, catalog, extraPropertyLine);
        }
        if (name.endsWith(".java")) {
            List<String> msgs = validateJava(fileName, content);
            if (directory != null && !msgs.isEmpty()) {
                msgs = withSiblingClassHints(msgs, BeanDeclarations.scan(directory, fileName));
            }
            return msgs;
        }
        if (name.endsWith(".xsl") || name.endsWith(".xslt")) {
            return validateXslt(content);
        }
        if (name.endsWith(".xml")) {
            return validateXml(content);
        }
        return List.of();
    }

    // ---- Java, XSLT and XML (CAMEL-24698): what camel run compiles or parses at startup, checked before writing ----

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

    private static final Pattern MISSING_METHOD_ON_EXCHANGE_PATTERN = Pattern.compile(
            "cannot find symbol\\s+symbol:\\s+method ((?:get|set)(?:Body|Header|Headers|Variable|Variables)[A-Za-z]*)\\([^)]*\\)\\s+location:.*type (org\\.apache\\.camel\\.Exchange)");

    private static final Pattern NULL_BRANCH_PATTERN
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

    private static final Pattern MISSING_CLASS_PATTERN = Pattern.compile("cannot find symbol\\s+symbol:\\s+class (\\w+)");
    private static final Pattern MISSING_PACKAGE_PATTERN = Pattern.compile("package ([\\w.]+) does not exist");

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

    /** Whether a line reads as text rather than code: no braces, semicolons or annotations, and more than two words. */
    static boolean looksLikeProse(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") || t.startsWith("@")) {
            return false;
        }
        for (char code : new char[] { '{', '}', ';', '(', ')', '=', '<', '>' }) {
            if (t.indexOf(code) >= 0) {
                return false;
            }
        }
        return t.split("\\s+").length > 2 || t.startsWith("#") || t.startsWith("```");
    }

    /**
     * Compiles the stylesheet the way the xslt components do: with Saxon when it is on the classpath (preferred, XSLT
     * 3.0), otherwise with the JDK processor (XSLT 1.0). A stylesheet that declares version 2.0 or 3.0 when only the
     * JDK processor is available is checked for well-formed XML only, since it is meant for the xslt-saxon component.
     */
    public static List<String> validateXslt(String content) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        javax.xml.transform.TransformerFactory factory = saxonFactory();
        boolean saxon = factory != null;
        Matcher vm = XSLT_VERSION_PATTERN.matcher(content);
        String version = vm.find() ? vm.group(1) : "1.0";
        if (!saxon && !version.startsWith("1")) {
            return validateXml(content);
        }
        if (factory == null) {
            factory = javax.xml.transform.TransformerFactory.newInstance();
        }
        List<String> collected = new ArrayList<>();
        javax.xml.transform.ErrorListener listener = new javax.xml.transform.ErrorListener() {
            @Override
            public void warning(javax.xml.transform.TransformerException e) {
            }

            @Override
            public void error(javax.xml.transform.TransformerException e) {
                collected.add(e.getMessageAndLocation());
            }

            @Override
            public void fatalError(javax.xml.transform.TransformerException e) {
                collected.add(e.getMessageAndLocation());
            }
        };
        try {
            factory.setErrorListener(listener);
            factory.newTemplates(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(content)));
        } catch (Exception e) {
            if (collected.isEmpty()) {
                collected.add(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
        for (String c : new java.util.LinkedHashSet<>(collected)) {
            String text = c.replace("\n", " ").trim();
            if (text.contains("Content is not allowed in prolog")) {
                // text, a blank line or a markdown fence before <?xml or <xsl:stylesheet
                String first = content.stripLeading().isEmpty() ? "" : content.stripLeading().split("\n", 2)[0].trim();
                text += " (the stylesheet must start with <?xml ...?> or <xsl:stylesheet; the file starts with \""
                        + (first.length() > 40 ? first.substring(0, 40) + "..." : first)
                        + "\"; remove everything before it, including blank lines)";
            }
            if (text.contains("Content is not allowed in trailing section")) {
                // an explanation appended after </xsl:stylesheet>
                String after = afterRootElement(content);
                text += " (the file must end with </xsl:stylesheet>; it continues with \""
                        + (after.length() > 40 ? after.substring(0, 40) + "..." : after)
                        + "\": put explanations in an <!-- XML comment --> or leave them out)";
            }
            msgs.add("XSLT: " + text);
        }
        boolean prolog = msgs.stream().anyMatch(m -> m.contains("not allowed in prolog")
                || m.contains("not allowed in trailing section"));
        if (!msgs.isEmpty() && !saxon && !prolog) {
            msgs.set(0, msgs.get(0) + " (compiled with the JDK processor, XSLT 1.0; for XSLT 2.0 or 3.0 functions such as"
                        + " current-dateTime() declare version=\"2.0\" and use the xslt-saxon component)");
        }
        if (prolog) {
            // the generic "Could not compile stylesheet" line adds nothing next to the prolog message
            msgs.removeIf(m -> m.equals("XSLT: Could not compile stylesheet"));
        }
        return msgs;
    }

    /** The first non-blank line after the closing tag of the root element (empty when there is none). */
    static String afterRootElement(String content) {
        Matcher m = Pattern.compile("</[A-Za-z_:][A-Za-z0-9_:.-]*\\s*>\\s*$", Pattern.MULTILINE).matcher(content);
        int end = -1;
        while (m.find()) {
            end = m.end();
        }
        if (end < 0) {
            return "";
        }
        // the last closing tag is the root only when the rest of the file is not XML; take the first text line after
        // the closing root tag: the closing tag whose remainder contains no further tag
        Matcher all = Pattern.compile("</[A-Za-z_:][A-Za-z0-9_:.-]*\\s*>").matcher(content);
        while (all.find()) {
            String rest = content.substring(all.end());
            if (!rest.contains("<") || rest.stripLeading().startsWith("<!--") && !rest.contains("</")) {
                for (String line : rest.split("\n")) {
                    if (!line.isBlank()) {
                        return line.trim();
                    }
                }
                return "";
            }
        }
        return "";
    }

    private static final Pattern XSLT_VERSION_PATTERN
            = Pattern.compile("<xsl:(?:stylesheet|transform)[^>]*\\sversion\\s*=\\s*[\"']([0-9.]+)[\"']");

    /** Saxon's factory when camel-xslt-saxon (or Saxon itself) is on the classpath, else null. */
    static javax.xml.transform.TransformerFactory saxonFactory() {
        try {
            Class<?> type = Class.forName("net.sf.saxon.TransformerFactoryImpl");
            return (javax.xml.transform.TransformerFactory) type.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            return null;
        }
    }

    /** Checks that the XML is well formed (an input file, a Camel XML DSL file or any other XML). */
    public static List<String> validateXml(String content) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(null);
            db.parse(new org.xml.sax.InputSource(new java.io.StringReader(content)));
        } catch (org.xml.sax.SAXParseException e) {
            String hint = "";
            if (e.getMessage() != null && e.getMessage().contains("not allowed in trailing section")) {
                String after = afterRootElement(content);
                hint = " (the file must end with the closing tag of its root element; it continues with \""
                       + (after.length() > 40 ? after.substring(0, 40) + "..." : after)
                       + "\": put explanations in an <!-- XML comment --> or leave them out)";
            } else if (e.getMessage() != null && e.getMessage().contains("not allowed in prolog")) {
                String first = content.stripLeading().isEmpty() ? "" : content.stripLeading().split("\n", 2)[0].trim();
                hint = " (the file must start with <?xml ...?> or its root element; it starts with \""
                       + (first.length() > 40 ? first.substring(0, 40) + "..." : first) + "\")";
            }
            msgs.add("Line " + e.getLineNumber() + ": XML is not well formed: " + e.getMessage() + hint);
        } catch (Exception e) {
            msgs.add("XML is not well formed: " + e.getMessage());
        }
        return msgs;
    }

    /**
     * Validates Camel YAML DSL source: the YAML DSL schema first, then endpoint URIs and simple expressions against the
     * catalog. Returns the messages, empty when the source is valid.
     */
    public static List<String> validateCamelYaml(String content, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        try {
            msgs.addAll(formatSchemaErrors(yamlValidator().validate(content)));
        } catch (Exception e) {
            msgs.add("Invalid YAML: " + e.getMessage());
            return msgs;
        }
        msgs.addAll(validateYamlCatalog(content, catalog));
        return msgs;
    }

    /**
     * The catalog checks of a YAML route without the schema: endpoint URIs and Simple expressions. For a caller that
     * has already validated the schema (the CLI, the schema tool).
     */
    public static List<String> validateYamlCatalog(String content, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank() || catalog == null) {
            return msgs;
        }
        msgs.addAll(validateYamlEndpoints(content, catalog));
        msgs.addAll(validateYamlSimple(content, catalog));
        msgs.addAll(validateKnownHeaders(content, catalog));
        return msgs;
    }

    private static final Pattern CAMEL_HEADER_REF_PATTERN = Pattern.compile(
            "(?:\\$\\{headers?\\.|headers\\.|headers\\[['\"]|header\\(['\"]|name:\\s*['\"]?)(Camel[A-Z][A-Za-z0-9]*)");
    private static final Pattern SCHEME_IN_URI_PATTERN = Pattern.compile("uri:\\s*\"?([a-zA-Z][a-zA-Z0-9+.-]*):");

    /**
     * A Camel* header that no component used in the file defines (CamelTimerIndex; the timer sets CamelTimerCounter):
     * the value is null at runtime. Checked against the header metadata of every component the file names, with the
     * closest real name.
     */
    public static List<String> validateKnownHeaders(String content, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null || catalog == null) {
            return msgs;
        }
        Set<String> known = new LinkedHashSet<>();
        Map<String, String> owner = new LinkedHashMap<>();
        Matcher sm = SCHEME_IN_URI_PATTERN.matcher(content);
        Set<String> schemes = new LinkedHashSet<>();
        while (sm.find()) {
            schemes.add(sm.group(1));
        }
        for (String scheme : schemes) {
            try {
                var model = catalog.componentModel(scheme);
                if (model != null) {
                    for (var h : model.getEndpointHeaders()) {
                        known.add(h.getName());
                        owner.putIfAbsent(h.getName(), scheme);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (known.isEmpty()) {
            return msgs;
        }
        // headers every exchange may carry, whatever the component
        Set<String> common = Set.of("CamelMessageTimestamp", "CamelFileName", "CamelFileNameProduced", "CamelCorrelationId",
                "CamelRedelivered", "CamelRedeliveryCounter", "CamelHttpResponseCode", "CamelHttpMethod", "CamelHttpPath",
                "CamelHttpQuery", "CamelHttpUri", "CamelHttpUrl", "CamelSplitIndex", "CamelSplitSize", "CamelSplitComplete",
                "CamelAggregatedSize", "CamelAggregatedCompletedBy", "CamelAggregatedCorrelationKey", "CamelLoopIndex",
                "CamelLoopSize", "CamelToEndpoint", "CamelBatchIndex", "CamelBatchSize", "CamelBatchComplete",
                "CamelFailureEndpoint", "CamelExceptionCaught", "CamelRouteStop", "CamelCharsetName", "CamelFileParent",
                "CamelFilePath", "CamelFileAbsolutePath", "CamelFileLength", "CamelFileLastModified", "CamelFileNameOnly",
                "CamelFileRelativePath", "CamelFileNameConsumed", "CamelFileExists", "CamelFileContentType",
                "CamelDuplicateMessage", "CamelSlipEndpoint", "CamelMulticastIndex",
                "CamelMulticastComplete", "CamelRecipientListEndpoint", "CamelReceivedTimestamp");
        String[] lines = content.split("\n", -1);
        Set<String> reported = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher m = CAMEL_HEADER_REF_PATTERN.matcher(lines[i]);
            while (m.find()) {
                String name = m.group(1);
                if (known.contains(name) || common.contains(name) || !reported.add(name)) {
                    continue;
                }
                String best = closestName(name, new ArrayList<>(known));
                String scheme = best != null && owner.containsKey(best.split(", ")[0]) ? owner.get(best.split(", ")[0]) : null;
                StringBuilder sb = new StringBuilder("Line ").append(i + 1).append(": header ").append(name)
                        .append(" is not set by ").append(String.join(", ", schemes)).append(" (the value would be null)");
                if (best != null) {
                    sb.append(": did you mean ").append(best).append("?");
                }
                if (scheme != null) {
                    List<String> names = new ArrayList<>();
                    for (var e : owner.entrySet()) {
                        if (e.getValue().equals(scheme)) {
                            names.add(e.getKey());
                        }
                    }
                    sb.append(" The ").append(scheme).append(" headers are ").append(String.join(", ", names)).append(".");
                }
                msgs.add(sb.toString());
            }
        }
        return msgs;
    }

    private static YamlValidator yamlValidator() throws Exception {
        YamlValidator v = yamlValidator;
        if (v == null) {
            synchronized (SourceValidator.class) {
                v = yamlValidator;
                if (v == null) {
                    v = new YamlValidator();
                    v.init();
                    yamlValidator = v;
                }
            }
        }
        return v;
    }

    // ---- bean references (CAMEL-24698) ----

    private static final Pattern BEAN_NAME_PATTERN = Pattern.compile("^\\s*-?\\s*name:\\s*(\\S+)\\s*$");
    private static final Pattern BEAN_REF_PATTERN = Pattern.compile(
            "^\\s*-?\\s*(ref|aggregationStrategy|strategyRef|processorRef|loadBalancerRef|executorServiceRef|onPrepareRef"
                                                                    + "|onRedeliveryRef|aggregationRepositoryRef|comparatorRef|bean|processor)"
                                                                    + ":\\s*(\\S+)\\s*$");
    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("\\b(?:public\\s+)?(?:final\\s+)?class\\s+(\\w+)");
    private static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern BIND_TO_REGISTRY_PATTERN
            = Pattern.compile("@BindToRegistry(?:\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"\\s*\\))?");

    /**
     * What the directory declares: bean names from sibling YAML files and Java classes annotated with
     * {@code @BindToRegistry}, plus the Java classes present (simple name to fully qualified name), so that a reference
     * to an unregistered class can name the fix.
     */
    public record BeanDeclarations(Set<String> names, Map<String, String> javaClasses, Map<String, String> javaSignatures,
            Map<String, Integer> javaPublicMethods, Map<String, List<String>> javaPublicMethodNames) {

        public BeanDeclarations(Set<String> names, Map<String, String> javaClasses, Map<String, String> javaSignatures) {
            this(names, javaClasses, javaSignatures, Map.of(), Map.of());
        }

        public BeanDeclarations(Set<String> names, Map<String, String> javaClasses, Map<String, String> javaSignatures,
                                Map<String, Integer> javaPublicMethods) {
            this(names, javaClasses, javaSignatures, javaPublicMethods, Map.of());
        }

        public static final BeanDeclarations NONE = new BeanDeclarations(Set.of(), Map.of(), Map.of());

        public BeanDeclarations(Set<String> names, Map<String, String> javaClasses) {
            this(names, javaClasses, Map.of());
        }

        /** Scans the directory, leaving out the file being validated (its own declarations come from the content). */
        public static BeanDeclarations scan(Path directory, String excludeFile) {
            Set<String> names = new HashSet<>();
            Map<String, String> classes = new LinkedHashMap<>();
            Map<String, String> signatures = new LinkedHashMap<>();
            Map<String, Integer> publicMethods = new LinkedHashMap<>();
            Map<String, List<String>> publicMethodNamesByClass = new LinkedHashMap<>();
            if (directory == null || !Files.isDirectory(directory)) {
                return NONE;
            }
            try (var stream = Files.list(directory)) {
                for (Path p : stream.filter(Files::isRegularFile).toList()) {
                    String fn = p.getFileName().toString();
                    if (fn.equals(excludeFile)) {
                        continue;
                    }
                    String lower = fn.toLowerCase(Locale.ROOT);
                    try {
                        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                            names.addAll(declaredBeans(Files.readString(p)));
                        } else if (lower.endsWith(".java")) {
                            String src = Files.readString(p);
                            Matcher cm = JAVA_CLASS_PATTERN.matcher(src);
                            Matcher pm = JAVA_PACKAGE_PATTERN.matcher(src);
                            String pkg = pm.find() ? pm.group(1) + "." : "";
                            if (cm.find()) {
                                String simple = cm.group(1);
                                classes.put(simple.toLowerCase(Locale.ROOT), pkg + simple);
                                // the class declaration up to its body: what it extends and implements
                                int brace = src.indexOf('{', cm.start());
                                signatures.put(simple.toLowerCase(Locale.ROOT),
                                        brace > 0 ? src.substring(cm.start(), brace) : src.substring(cm.start()));
                                List<String> methodNames = publicMethodNames(src, simple);
                                publicMethods.put(simple.toLowerCase(Locale.ROOT), methodNames.size());
                                publicMethodNamesByClass.put(simple.toLowerCase(Locale.ROOT), methodNames);
                                Matcher bm = BIND_TO_REGISTRY_PATTERN.matcher(src);
                                while (bm.find()) {
                                    String n = bm.group(1);
                                    names.add(n != null ? n : Character.toLowerCase(simple.charAt(0)) + simple.substring(1));
                                }
                            }
                        }
                    } catch (IOException e) {
                        // unreadable sibling: nothing to learn from it
                    }
                }
            } catch (IOException e) {
                return NONE;
            }
            return new BeanDeclarations(names, classes, signatures, publicMethods, publicMethodNamesByClass);
        }
    }

    private static final Pattern PUBLIC_METHOD_PATTERN = Pattern.compile(
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

    private static final Pattern BEAN_TYPE_PATTERN = Pattern.compile("^\\s*type:\\s*[\"']?#class:([\\w.$]+)");

    /** The beans declared under {@code beans:} with a {@code #class:} type, name to fully qualified class name. */
    static Map<String, String> declaredBeanTypes(String content) {
        Map<String, String> types = new LinkedHashMap<>();
        if (content == null) {
            return types;
        }
        String last = null;
        for (String line : content.split("\n", -1)) {
            Matcher nm = BEAN_NAME_PATTERN.matcher(line);
            if (nm.find()) {
                last = unquote(nm.group(1));
                continue;
            }
            Matcher tm = BEAN_TYPE_PATTERN.matcher(line);
            if (tm.find() && last != null) {
                types.put(last, tm.group(1));
            }
        }
        return types;
    }

    /** The interface an option's bean must implement, for the options where a wrong class is a common mistake. */
    private static final Map<String, String> REQUIRED_TYPES = Map.of(
            "aggregationStrategy", "AggregationStrategy",
            "strategyRef", "AggregationStrategy",
            "processorRef", "Processor",
            "processor", "Processor");

    /** The bean names declared under {@code beans:} in the YAML content. */
    public static Set<String> declaredBeans(String content) {
        Set<String> names = new HashSet<>();
        if (content == null) {
            return names;
        }
        String[] lines = content.split("\n", -1);
        int blockIndent = -1;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            int indent = countLeadingSpaces(line);
            if (blockIndent >= 0 && indent <= blockIndent) {
                blockIndent = -1;
            }
            if (blockIndent < 0) {
                if (trimmed.equals("- beans:") || trimmed.equals("beans:")) {
                    blockIndent = indent;
                }
                continue;
            }
            Matcher m = BEAN_NAME_PATTERN.matcher(line);
            if (m.find()) {
                names.add(unquote(m.group(1)));
            }
        }
        return names;
    }

    /**
     * Bean references in the YAML that nothing declares, each with how to declare it. A reference that is a
     * {@code #class:}, {@code #type:} or {@code #bean:} value, a property placeholder, or a class name is left alone.
     */
    private static final Pattern SIMPLE_BEAN_FUNCTION = Pattern.compile("\\$\\{bean:([A-Za-z_][\\w-]*)");

    public static List<String> validateYamlBeanRefs(String content, BeanDeclarations external) {
        return validateYamlBeanRefs(content, external, null);
    }

    /** As above, and with a catalog the messages about a strategy name the built-in implementations. */
    public static List<String> validateYamlBeanRefs(String content, BeanDeclarations external, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null) {
            return msgs;
        }
        Set<String> declared = new HashSet<>(declaredBeans(content));
        if (external != null) {
            declared.addAll(external.names());
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            // ${bean:name.method} in a simple expression is a reference too; the catalog cannot check it
            Matcher bm = SIMPLE_BEAN_FUNCTION.matcher(lines[i]);
            while (bm.find()) {
                String ref = bm.group(1);
                int after = bm.end();
                if (after < lines[i].length() && lines[i].charAt(after) == ':'
                        && !(after + 1 < lines[i].length() && lines[i].charAt(after + 1) == ':')) {
                    // ${bean:name:method}: the method is not separated by a colon
                    String method = lines[i].substring(after + 1).split("[^\\w]", 2)[0];
                    msgs.add("Line " + (i + 1) + ": ${bean:" + ref + ":" + method + "}: the method is written as ${bean:" + ref
                             + "." + method + "}, ${bean:" + ref + "?method=" + method + "} or ${bean:" + ref + "::" + method
                             + "}, not with a single colon (the whole '" + ref + ":" + method
                             + "' would be looked up as the bean name)");
                    continue;
                }
                if (!declared.contains(ref)) {
                    addUndeclared(msgs, i, "${bean:" + ref + "}", ref, external);
                }
            }
            Matcher tm = BEAN_TYPE_PATTERN.matcher(lines[i]);
            if (tm.find()) {
                String missing = classNotFound(tm.group(1), external);
                if (missing != null) {
                    msgs.add("Line " + (i + 1) + ": type: " + missing);
                }
                continue;
            }
            Matcher m = BEAN_REF_PATTERN.matcher(lines[i]);
            if (!m.find()) {
                continue;
            }
            String option = m.group(1);
            String ref = unquote(m.group(2));
            if (ref.isEmpty() || ref.contains("{{") || ref.contains("${") || ref.startsWith("&") || ref.startsWith("*")
                    || (ref.contains(":") && !ref.startsWith("#class:"))) {
                continue;
            }
            String required = REQUIRED_TYPES.get(option);
            if (ref.startsWith("#class:")) {
                String missing = classNotFound(ref.substring("#class:".length()), external);
                if (missing != null) {
                    msgs.add("Line " + (i + 1) + ": " + option + ": " + missing
                             + (required != null ? builtInHint(catalog, "org.apache.camel." + required) : ""));
                }
            }
            if (required != null && external != null) {
                // a class is given or declared: does it implement what the option needs
                String fqcn
                        = ref.startsWith("#class:") ? ref.substring("#class:".length()) : declaredBeanTypes(content).get(ref);
                if (fqcn != null) {
                    String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                    String signature = external.javaSignatures().get(simple);
                    if (signature != null && !signature.contains(required)) {
                        Integer methods = external.javaPublicMethods().get(simple);
                        if (option.equals("aggregationStrategy") && methods != null && methods == 1) {
                            // a POJO with one public method is adapted by the runtime
                            continue;
                        }
                        if (option.equals("aggregationStrategy") && methods != null && methods > 1) {
                            if (!content.contains("aggregationStrategyMethodName")) {
                                msgs.add("Line " + (i + 1) + ": " + option + ": " + fqcn + " does not implement org.apache"
                                         + ".camel." + required + " and has " + methods + " public methods: the runtime"
                                         + " cannot pick one, set aggregationStrategyMethodName: <method> or implement"
                                         + " the interface");
                            }
                            continue;
                        }
                        msgs.add("Line " + (i + 1) + ": " + option + ": " + fqcn + " must implement org.apache.camel."
                                 + required + " (its declaration is: " + signature.replaceAll("\\s+", " ").trim() + ")"
                                 + (option.equals("aggregationStrategy")
                                         ? ", or be a POJO with one public method such as append(String a, String b)"
                                         : "")
                                 + builtInHint(catalog, "org.apache.camel." + required));
                    }
                }
            }
            if ((option.equals("ref") || option.equals("bean")) && external != null && !hasMethodNearby(lines, i)) {
                // - bean: {ref: x} on a class with several public methods: the runtime cannot pick one
                String fqcn
                        = ref.startsWith("#class:") ? ref.substring("#class:".length()) : declaredBeanTypes(content).get(ref);
                if (fqcn != null) {
                    String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                    List<String> methodNames = external.javaPublicMethodNames().get(simple);
                    if (methodNames != null && methodNames.size() > 1 && isBeanStep(lines, i)) {
                        msgs.add("Line " + (i + 1) + ": bean " + ref + " has " + methodNames.size() + " public methods ("
                                 + String.join(", ", methodNames) + ") and the step names none: add method: <name>"
                                 + " next to ref:, or annotate the one to call with @Handler");
                    }
                }
            }
            int dot = ref.lastIndexOf('.');
            if (dot > 0 && dot < ref.length() - 1 && Character.isUpperCase(ref.charAt(dot + 1))) {
                // a fully qualified class name
                continue;
            }
            if (ref.startsWith("#")) {
                continue;
            }
            if (declared.contains(ref)) {
                continue;
            }
            addUndeclared(msgs, i, option, ref, external);
        }
        return msgs;
    }

    /** "; the built-in ones are StringAggregationStrategy (org...), ..." from the catalog's bean metadata, or empty. */
    static String builtInHint(CamelCatalog catalog, String interfaceName) {
        List<String> beans = CatalogDocs.beansOfInterface(catalog, interfaceName);
        if (beans.isEmpty()) {
            return "";
        }
        List<String> shown = beans.size() > 6 ? beans.subList(0, 6) : beans;
        return "; the built-in ones are " + String.join(", ", shown)
               + (beans.size() > 6
                       ? " and " + (beans.size() - 6) + " more (camel_catalog_find with kind bean lists them)" : "");
    }

    /** Whether a method: line sits in the same block as the ref: line at index i (same indentation, adjacent). */
    private static boolean hasMethodNearby(String[] lines, int i) {
        int indent = indentOf(lines[i]);
        for (int d = -1; d <= 1; d += 2) {
            for (int j = i + d; j >= 0 && j < lines.length; j += d) {
                if (lines[j].isBlank()) {
                    continue;
                }
                int ind = indentOf(lines[j]);
                if (ind < indent || lines[j].trim().startsWith("- ") && ind <= indent) {
                    break;
                }
                if (ind == indent && lines[j].trim().startsWith("method:")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the ref: line at index i belongs to a - bean: step (the line above is the bean: key). */
    private static boolean isBeanStep(String[] lines, int i) {
        if (lines[i].trim().startsWith("bean:")) {
            return true;
        }
        for (int j = i - 1; j >= 0; j--) {
            if (lines[j].isBlank()) {
                continue;
            }
            if (indentOf(lines[j]) < indentOf(lines[i])) {
                return lines[j].trim().matches("-?\\s*bean:\\s*");
            }
            if (lines[j].trim().startsWith("- ")) {
                return lines[j].trim().matches("-\\s*bean:\\s*");
            }
        }
        return false;
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /**
     * A class named with its package that is neither next to the route nor on the CLI classpath: the wrong package
     * (org.apache.camel.support.StringAggregationStrategy) or a missing dependency. Null when the class is fine.
     */
    static String classNotFound(String fqcn, BeanDeclarations external) {
        if (external == null || external == BeanDeclarations.NONE
                || fqcn == null || fqcn.contains("{{") || fqcn.contains("${")) {
            // without the directory the class may well be next to the route: do not judge
            return null;
        }
        if (fqcn.contains("$")) {
            // Outer$Inner: camel run compiles the files next to the route as top-level classes
            String outer = fqcn.substring(0, fqcn.indexOf('$'));
            String inner = fqcn.substring(fqcn.indexOf('$') + 1);
            return "class " + fqcn + " is an inner class of " + outer + ": a bean type must be a top-level class, move "
                   + inner + " to its own file " + inner + ".java next to the route and write #class:" + inner;
        }
        if (!fqcn.contains(".")) {
            return null;
        }
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        if (external != null && external.javaClasses().containsKey(simple.toLowerCase(Locale.ROOT))) {
            return null;
        }
        try {
            Class.forName(fqcn, false, SourceValidator.class.getClassLoader());
            return null;
        } catch (Throwable e) {
            // not on the classpath
        }
        String hint = "";
        if (simple.endsWith("AggregationStrategy") && !fqcn.startsWith("org.apache.camel.processor.aggregate.")) {
            String candidate = "org.apache.camel.processor.aggregate." + simple;
            try {
                Class.forName(candidate, false, SourceValidator.class.getClassLoader());
                hint = " (did you mean " + candidate + "?)";
            } catch (Throwable e) {
                // not a built-in strategy
            }
        }
        if (hint.isEmpty()) {
            hint = " (check the package name; a class of your own goes in a .java file next to the route, a class from"
                   + " another library needs its dependency)";
        }
        return "class " + fqcn + " was not found" + hint;
    }

    private static void addUndeclared(List<String> msgs, int i, String option, String ref, BeanDeclarations external) {
        String fqcn = external != null ? external.javaClasses().get(ref.toLowerCase(Locale.ROOT)) : null;
        if (fqcn != null) {
            String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            msgs.add("Line " + (i + 1) + ": " + option + ": bean '" + ref + "' is not declared: " + simple
                     + ".java is in the directory, but a Java class is not a bean until it is declared; add\n"
                     + "- beans:\n  - name: " + ref + "\n    type: \"#class:" + fqcn + "\"\n"
                     + (option.startsWith("${bean:")
                             ? "" : "or reference the class directly: " + option + ": \"#class:" + fqcn + "\""));
        } else {
            msgs.add("Line " + (i + 1) + ": " + option + ": bean '" + ref + "' is not declared in this file or its"
                     + " directory: declare it under - beans: (name: " + ref
                     + ", type: \"#class:<fully qualified class>\")"
                     + (option.startsWith("${bean:")
                             ? "" : ", or reference the class directly with #class:<fully qualified class>"));
        }
    }

    /**
     * Validates a properties file line by line: {@code camel.*} keys against the catalog, the rest with the extra
     * check.
     */
    public static List<String> validateProperties(
            String content, CamelCatalog catalog, Function<String, String> extraPropertyLine) {
        return validatePropertiesLines(content, line -> validatePropertyLine(line, catalog, extraPropertyLine));
    }

    /** Validates one properties line: a {@code camel.*} key against the catalog, any other with the extra check. */
    private static final Pattern COMPONENT_KEY_PATTERN
            = Pattern.compile("^\\s*camel\\.(component|dataformat|language)\\.([A-Za-z0-9-]+)\\.");

    private static final Pattern ROOT_LEVEL_PATTERN
            = Pattern.compile("^\\s*logging\\.level\\.root\\s*=\\s*(WARN|WARNING|ERROR|FATAL|OFF)\\s*$",
                    Pattern.CASE_INSENSITIVE);

    public static String validatePropertyLine(String line, CamelCatalog catalog, Function<String, String> extra) {
        Matcher rl = ROOT_LEVEL_PATTERN.matcher(line);
        if (rl.find()) {
            // the log EIP logs under the route file's name, so a root level above INFO hides the route's own output
            return "logging.level.root=" + rl.group(1) + " also hides the route's own log steps (they log at INFO under"
                   + " the route file's name): keep root at INFO, or add logging.level.<route-file-name>=INFO";
        }
        if (catalog != null) {
            // camel.component.logger.level: the catalog skips a component it does not know, camel run does not
            Matcher km = COMPONENT_KEY_PATTERN.matcher(line);
            if (km.find()) {
                String kind = km.group(1);
                String name = km.group(2);
                List<String> known = switch (kind) {
                    case "component" -> catalog.findComponentNames();
                    case "dataformat" -> catalog.findDataFormatNames();
                    default -> catalog.findLanguageNames();
                };
                if (!known.contains(name)) {
                    String closest = closestName(name, known);
                    return name + "    Unknown " + kind + (closest != null ? " (did you mean " + closest + "?)" : "");
                }
            }
            try {
                ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(line);
                if (result.isAccepted()) {
                    if (!result.isSuccess()) {
                        String msg = result.summaryErrorMessage(false);
                        if (msg != null) {
                            msg = msg.trim();
                            String hint = mainOptionHint(line, catalog);
                            if (hint == null && km.reset().find() && "component".equals(km.group(1))) {
                                hint = endpointOptionHint(km.group(2), line, catalog);
                            }
                            return hint != null ? msg + " " + hint : msg;
                        }
                    }
                    return null;
                }
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        return extra != null ? extra.apply(line) : null;
    }

    /**
     * For an unknown camel.main.* key, the closest real option name (camel.main.duration -> durationMaxSeconds). For an
     * option that exists in another group of the main configuration, that group (camel.component.netty-http
     * .binding-mode -> camel.rest.bindingMode; camel.main.binding-mode -> camel.rest.bindingMode).
     */
    static String mainOptionHint(String line, CamelCatalog catalog) {
        String key = line.contains("=") ? line.substring(0, line.indexOf('=')).trim() : line.trim();
        if (!key.startsWith("camel.")) {
            return null;
        }
        String option = key.substring(key.lastIndexOf('.') + 1);
        String camel = dashToCamelCase(option);
        boolean main = key.startsWith("camel.main.");
        if (main && camel.toLowerCase(Locale.ROOT).matches("duration(strict|check|strictcheck|logging|logger)[a-z]*")) {
            // camel.main.durationStrictCheck=false, durationLoggingEnabled=true: nothing to switch off or on
            return "(there is no such switch: camel.main.duration=15s (or durationMaxSeconds=15) is all that limits a"
                   + " run, and without it the application runs until stopped; remove the line)";
        }
        if (main && camel.toLowerCase(Locale.ROOT).matches("log(ging)?level|log(ging)?")) {
            // camel.main.loggingLevel=INFO: the log level is a logging.level.* key, not a camel.* option
            return "(the log level is set with logging.level.root=INFO, or logging.level.<package>=DEBUG for one"
                   + " package)";
        }
        try {
            List<String> known = new ArrayList<>();
            List<String> elsewhere = new ArrayList<>();
            for (var o : catalog.mainModel().getOptions()) {
                String n = o.getName();
                if (n.startsWith("camel.main.")) {
                    known.add(n.substring("camel.main.".length()));
                }
                String group = n.substring(0, n.lastIndexOf('.'));
                String name = n.substring(n.lastIndexOf('.') + 1);
                if (!key.startsWith(group + ".") && name.equalsIgnoreCase(camel)) {
                    elsewhere.add(n);
                }
                // camel.main.restComponent: the group's own name folded into the option (camel.rest.component)
                String groupWord = group.substring(group.lastIndexOf('.') + 1);
                if (main && camel.length() > groupWord.length() && camel.toLowerCase(Locale.ROOT).startsWith(groupWord)
                        && Character.isUpperCase(camel.charAt(groupWord.length()))
                        && name.equalsIgnoreCase(camel.substring(groupWord.length()))) {
                    return "(did you mean " + n + "? the " + groupWord + " options are the camel." + groupWord + ".* keys)";
                }
            }
            if (main) {
                if (known.contains(camel)) {
                    return !camel.equals(option) ? "(did you mean camel.main." + camel + "?)" : null;
                }
                List<String> closest = new ArrayList<>(closestNames(camel, known));
                if (!closest.isEmpty()) {
                    String note = "";
                    if (closest.remove("durationMaxSeconds")) {
                        // camel.main.duration=60s: the option a run limit means, and its value has no unit
                        closest.add(0, "durationMaxSeconds");
                        note = "; durationMaxSeconds is a number of seconds without a unit, such as 60";
                    }
                    return "(did you mean " + String.join(", ", closest.stream().map(c -> "camel.main." + c).toList())
                           + "?" + note + ")";
                }
            }
            if ((camel.equals("enabled") || camel.equals("enable")) && !main) {
                // camel.resilience4j.enabled=true: the group has no switch, it applies when the route uses the feature
                String group = key.substring(0, key.lastIndexOf('.'));
                List<String> options = new ArrayList<>();
                for (var o : catalog.mainModel().getOptions()) {
                    if (o.getName().startsWith(group + ".")) {
                        options.add(o.getName().substring(group.length() + 1));
                    }
                }
                if (!options.isEmpty()) {
                    return "(" + group + " has no enabled switch: its settings apply when a route uses the feature; the options"
                           + " are " + String.join(", ", options.size() > 8 ? options.subList(0, 8) : options)
                           + (options.size() > 8 ? ", ..." : "") + ")";
                }
            }
            if (!elsewhere.isEmpty()) {
                // the groups a route author means most often first: camel.rest before camel.management
                elsewhere.sort(java.util.Comparator.comparingInt(n -> groupRank(n.substring(0, n.lastIndexOf('.')))));
                List<String> groups = elsewhere.stream().map(n -> n.substring(0, n.lastIndexOf('.'))).limit(3).toList();
                String where = groups.size() == 1
                        ? groups.get(0)
                        : String.join(", ", groups.subList(0, groups.size() - 1)) + " or " + groups.get(groups.size() - 1);
                return "(did you mean " + elsewhere.get(0) + "? " + option + " is an option of " + where
                       + (main ? "" : ", not of the " + key.substring(0, key.lastIndexOf('.')) + " key") + ")";
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * camel.component.timer.period=1000: period is an endpoint option (it goes in the uri), the camel.component.* keys
     * are the component's own options; say so and name a few of those.
     */
    static String endpointOptionHint(String scheme, String line, CamelCatalog catalog) {
        try {
            String key = line.contains("=") ? line.substring(0, line.indexOf('=')).trim() : line.trim();
            String option = dashToCamelCase(key.substring(key.lastIndexOf('.') + 1));
            var model = catalog.componentModel(scheme);
            if (model == null) {
                return null;
            }
            boolean endpointOption = model.getEndpointOptions().stream().anyMatch(o -> o.getName().equals(option));
            if (!endpointOption) {
                return null;
            }
            List<String> componentOptions = model.getComponentOptions().stream().map(o -> o.getName()).limit(6).toList();
            return "(" + option + " is an endpoint option of " + scheme + ": set it in the uri, " + scheme + ":name?" + option
                   + "=...; the camel.component." + scheme + ".* keys are the component's own options"
                   + (componentOptions.isEmpty() ? "" : ": " + String.join(", ", componentOptions)) + ")";
        } catch (Exception e) {
            return null;
        }
    }

    private static final List<String> GROUP_ORDER
            = List.of("camel.main", "camel.rest", "camel.server", "camel.management", "camel.health", "camel.metrics");

    private static int groupRank(String group) {
        int i = GROUP_ORDER.indexOf(group);
        return i < 0 ? GROUP_ORDER.size() : i;
    }

    private static String dashToCamelCase(String text) {
        if (!text.contains("-")) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char ch : text.toCharArray()) {
            if (ch == '-') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return sb.toString();
    }

    static String closestName(String name, List<String> known) {
        List<String> all = closestNames(name, known);
        return all.isEmpty() ? null : String.join(", ", all);
    }

    /** Up to three names within edit distance, the closest first; a prefix match counts as distance 1. */
    static List<String> closestNames(String name, List<String> known) {
        int threshold = Math.max(2, name.length() / 3);
        String lower = name.toLowerCase(Locale.ROOT);
        Map<String, Integer> distances = new LinkedHashMap<>();
        for (String k : known) {
            String kl = k.toLowerCase(Locale.ROOT);
            int d = lower.startsWith(kl) || kl.startsWith(lower) ? 1 : editDistance(lower, kl);
            if (d <= threshold) {
                distances.put(k, d);
            }
        }
        if (distances.isEmpty()) {
            // durationStyle: nothing within edit distance, so the options that start with the same word (duration)
            String word = leadingWord(name).toLowerCase(Locale.ROOT);
            if (word.length() >= 5) {
                for (String k : known) {
                    if (k.toLowerCase(Locale.ROOT).startsWith(word)) {
                        distances.put(k, 1);
                    }
                }
            }
        }
        return distances.entrySet().stream()
                .sorted(Map.Entry.<String, Integer> comparingByValue().thenComparing(e -> e.getKey().length())
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** The first camelCase or dash-separated word of a name (durationStyle, duration-style -> duration). */
    private static String leadingWord(String name) {
        int i = 0;
        while (i < name.length() && Character.isLowerCase(name.charAt(i))) {
            i++;
        }
        return name.substring(0, i);
    }

    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** The index of the first unescaped '=' or ':' in a properties line, or -1. */
    private static int firstSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\\') {
                i++;
            } else if (ch == '=' || ch == ':') {
                return i;
            }
        }
        return -1;
    }

    /** Runs a line validator over the key=value lines of a properties file, prefixing each message with its line. */
    public static List<String> validatePropertiesLines(String content, Function<String, String> lineValidator) {
        List<String> msgs = new ArrayList<>();
        if (content == null) {
            return msgs;
        }
        String[] lines = content.split("\n", -1);
        boolean continued = false;
        int prose = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            boolean wasContinued = continued;
            continued = line.endsWith("\\");
            if (wasContinued || line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int sep = firstSeparator(line);
            String key = sep < 0 ? line : line.substring(0, sep).trim();
            if (key.isEmpty() || key.contains(" ") || key.contains("\t") || sep < 0 && key.startsWith("`")) {
                {
                    // an explanation appended after the properties: java.util.Properties reads it as keys with no
                    // value, so camel run does not fail on it, but it is not what the author meant
                    if (++prose <= 3) {
                        msgs.add("Line " + (i + 1) + " is not a property (\""
                                 + (line.length() > 40 ? line.substring(0, 40) + "..." : line)
                                 + "\"): a properties file holds key=value lines; put explanations in a # comment or leave"
                                 + " them out");
                    } else if (prose == 4) {
                        msgs.add("Line " + (i + 1) + " and the lines after it: more text that is not a property; the"
                                 + " file must end with its last key=value line");
                    }
                }
                continue;
            }
            if (!line.contains("=")) {
                continue;
            }
            String error = lineValidator.apply(lines[i]);
            if (error != null) {
                msgs.add("Line " + (i + 1) + ": " + error);
            }
        }
        return msgs;
    }

    /** The YAML DSL schema errors in words: the node they are about and the message without parser noise. */
    public static List<String> formatSchemaErrors(List<Error> errors) {
        List<String> msgs = new ArrayList<>();
        if (errors == null) {
            return msgs;
        }
        for (Error error : errors) {
            String msg = error.getMessage();
            if (msg == null) {
                continue;
            }
            String loc = error.getInstanceLocation() != null ? error.getInstanceLocation().toString() : null;
            String node = extractNodeName(loc);
            String clean = cleanValidationMessage(msg);
            msgs.add(node != null ? node + ": " + clean : clean);
        }
        return msgs;
    }

    public static String cleanValidationMessage(String msg) {
        // strip FQCN prefix like "com.fasterxml...MarkedYAMLException: "
        int colonSpace = msg.indexOf(": ");
        if (colonSpace > 0) {
            String prefix = msg.substring(0, colonSpace);
            if (prefix.contains(".") && !prefix.contains(" ")) {
                msg = msg.substring(colonSpace + 2);
            }
        }
        // strip "at [Source: (StringReader); line: N, column: N]"
        int atSource = msg.indexOf("at [Source:");
        if (atSource > 0) {
            msg = msg.substring(0, atSource).stripTrailing();
        }
        // strip "in 'reader', " prefix from snakeyaml messages
        msg = msg.replace("in 'reader', ", "");
        return msg;
    }

    public static String extractNodeName(String instanceLocation) {
        if (instanceLocation == null || instanceLocation.isEmpty()) {
            return null;
        }
        int slash = instanceLocation.lastIndexOf('/');
        String last = slash >= 0 ? instanceLocation.substring(slash + 1) : instanceLocation;
        if (last.isEmpty()) {
            return null;
        }
        // skip pure numeric segments (array indices)
        try {
            Integer.parseInt(last);
            return null;
        } catch (NumberFormatException e) {
            return last;
        }
    }

    /** The simple expressions of a YAML route checked against the catalog, as predicate where the EIP expects one. */
    public static List<String> validateYamlSimple(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            String simpleText = null;
            int lineNum = i + 1;
            int lineIndent = countLeadingSpaces(line);
            boolean isLogMessage = false;

            // Strip YAML list prefix for matching
            String key = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;

            // Match "simple: <value>" (inline shorthand)
            if (key.startsWith("simple:") && !key.equals("simple:")) {
                simpleText = extractYamlValue(key, "simple");
            }
            // Match "simple:" followed by "expression: <value>" on next line
            else if (key.equals("simple:")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.isBlank()) {
                        continue;
                    }
                    if (next.startsWith("expression:")) {
                        simpleText = extractYamlValue(next, "expression");
                        lineNum = j + 1;
                    }
                    break;
                }
            }
            // Match "message: <value>" under log: EIP
            else if (key.startsWith("message:") && !key.equals("message:")) {
                String parentEip = findParentEip(lines, i, lineIndent);
                if ("log".equals(parentEip)) {
                    simpleText = extractYamlValue(key, "message");
                    isLogMessage = true;
                }
            }

            if (simpleText == null || simpleText.isEmpty()) {
                continue;
            }
            // Skip placeholder-only expressions
            if (simpleText.startsWith("{{") && simpleText.endsWith("}}")) {
                continue;
            }

            // Determine predicate vs expression context
            boolean predicate = false;
            if (!isLogMessage) {
                String parentEip = findParentEip(lines, i, lineIndent);
                predicate = parentEip != null && PREDICATE_EIPS.contains(parentEip);
            }

            try {
                LanguageValidationResult result = predicate
                        ? catalog.validateLanguagePredicate(null, "simple", simpleText)
                        : catalog.validateLanguageExpression(null, "simple", simpleText);
                if (!result.isSuccess()) {
                    String error = result.getShortError() != null ? result.getShortError() : result.getError();
                    if (error != null) {
                        errors.add("Line " + lineNum + ": Simple syntax error: " + error
                                   + aggregatedSizeHint(error, lines, i, lineIndent));
                    }
                }
            } catch (Exception e) {
                // best effort
            }
        }
        return errors;
    }

    /**
     * ${size} or ${count} written inside an aggregate: the parser's did-you-mean (${length}) is about the function,
     * what the author wants is the number of aggregated messages, an exchange property.
     */
    static String aggregatedSizeHint(String error, String[] lines, int lineIdx, int lineIndent) {
        if (!error.contains("Unknown function: size") && !error.contains("Unknown function: count")) {
            return "";
        }
        if (!hasAncestorEip(lines, lineIdx, lineIndent, "aggregate")) {
            return "";
        }
        return " (inside an aggregate the number of aggregated messages is"
               + " ${exchangeProperty.CamelAggregatedSize}, and ${exchangeProperty.CamelAggregatedCompletedBy}"
               + " says what completed the group)";
    }

    /** Whether one of the EIPs the line is nested in (any depth) is the given one. */
    static boolean hasAncestorEip(String[] lines, int lineIdx, int lineIndent, String eip) {
        int indent = lineIndent;
        for (int j = lineIdx - 1; j >= 0 && indent > 0; j--) {
            String prev = lines[j];
            if (prev.isBlank() || prev.trim().startsWith("#")) {
                continue;
            }
            int prevIndent = countLeadingSpaces(prev);
            if (prevIndent < indent) {
                if (eip.equals(extractEipFromLine(prev.trim()))) {
                    return true;
                }
                indent = prevIndent;
            }
        }
        return false;
    }

    /** The endpoint URIs of a YAML route (uri plus a parameters: map) checked against the catalog. */
    /** Components whose endpoint path is a file next to the route (a stylesheet, a template). */
    private static final Set<String> RESOURCE_SCHEMES
            = Set.of("xslt", "xslt-saxon", "velocity", "freemarker", "mustache", "jslt", "thymeleaf", "string-template",
                    "chunk", "language");

    /**
     * An xslt:stylesheets/x.xsl (or velocity, freemarker...) whose file is not in the directory fails when the route
     * starts; with camel run the files next to the route are on the classpath root, so the endpoint names the file
     * without a folder. Says what is missing and, when a file of that name is in the directory, the endpoint to write.
     */
    public static List<String> validateResourceRefs(String content, Path directory) {
        List<String> errors = new ArrayList<>();
        if (content == null || directory == null || !Files.isDirectory(directory)) {
            return errors;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = YAML_URI_PATTERN.matcher(lines[i]);
            if (!m.find() || lines[i].trim().startsWith("#")) {
                continue;
            }
            String uri = m.group(1);
            if (uri.endsWith("\"")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            int colon = uri.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String scheme = uri.substring(0, colon);
            if (!RESOURCE_SCHEMES.contains(scheme)) {
                continue;
            }
            String path = uri.substring(colon + 1);
            if (path.startsWith("//")) {
                path = path.substring(2);
            }
            int q = path.indexOf('?');
            if (q >= 0) {
                path = path.substring(0, q);
            }
            if (scheme.equals("language")) {
                // language:xpath:resource:classpath:foo.xpath or language:xslt:file:foo.xsl
                int c = path.indexOf(':');
                if (c < 0) {
                    continue;
                }
                path = path.substring(c + 1);
                if (path.startsWith("resource:")) {
                    path = path.substring("resource:".length());
                } else {
                    continue;
                }
            }
            if (path.startsWith("file:")) {
                path = path.substring("file:".length());
            } else if (path.startsWith("classpath:")) {
                path = path.substring("classpath:".length());
            } else if (path.contains(":") || path.startsWith("{{") || path.isBlank()) {
                continue; // http:, ref:, bean:, a placeholder
            }
            if (Files.exists(directory.resolve(path))) {
                continue;
            }
            String base = path.substring(path.lastIndexOf('/') + 1);
            String hint;
            if (!base.isEmpty() && Files.exists(directory.resolve(base))) {
                hint = " (the directory has " + base + ": the files next to the route are found by name, write "
                       + scheme + ":" + base + ")";
            } else {
                hint = " (add the file next to the route files and name it by file name alone: " + scheme + ":<name>)";
            }
            errors.add("Line " + (i + 1) + ": " + scheme + ": the file " + path + " does not exist in the directory"
                       + hint);
        }
        return errors;
    }

    public static List<String> validateYamlEndpoints(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            Matcher m = YAML_URI_PATTERN.matcher(line);
            if (!m.find()) {
                continue;
            }

            String uri = m.group(1);
            if (uri.endsWith("\"")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            if (uri.startsWith("{{")) {
                continue;
            }
            Matcher several = SEVERAL_ENDPOINTS_PATTERN.matcher(uri);
            if (several.find()) {
                // to: direct:a,direct:b : one endpoint per to:, several go through multicast or recipientList
                errors.add(linePrefix(i) + "a to: takes one endpoint; \"" + uri + "\" names several: send to each with"
                           + " multicast: {to: [...]} (all of them) or recipientList: {constant: \"" + uri + "\"}"
                           + " (a list evaluated at runtime), or write one - to: step per endpoint");
                continue;
            }
            // scheme-only URI (e.g., "uri: timer") needs a colon for catalog parsing
            if (!uri.contains(":")) {
                uri = uri + ":";
            }

            String eipName = extractEipFromLine(trimmed);
            {
                // from: mock:result, from: log:x : a producer-only component cannot be consumed from
                String scheme0 = uri.substring(0, uri.indexOf(':'));
                boolean isFrom = "from".equals(eipName)
                        || "uri".equals(eipName) && "from".equals(findParentEip(lines, i, countLeadingSpaces(line)));
                if (isFrom) {
                    try {
                        var cm = catalog.componentModel(scheme0);
                        if (cm != null && cm.isProducerOnly()) {
                            errors.add(linePrefix(i) + scheme0 + " is a producer-only component: it cannot be a from:"
                                       + " (the runtime says 'You cannot consume from this endpoint'); to pass messages"
                                       + " between routes send with to: direct:name and consume with from: direct:name"
                                       + " (or seda: for a queue)");
                            continue;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            int lineIndent = countLeadingSpaces(line);

            // for "uri:" lines, walk backwards to find the parent EIP (from, to, etc.)
            if ("uri".equals(eipName)) {
                for (int j = i - 1; j >= 0; j--) {
                    String prev = lines[j];
                    if (prev.isBlank()) {
                        continue;
                    }
                    int prevIndent = countLeadingSpaces(prev);
                    if (prevIndent < lineIndent) {
                        eipName = extractEipFromLine(prev.trim());
                        break;
                    }
                }
            }

            boolean consumerOnly = eipName != null && CONSUMER_EIPS.contains(eipName);
            boolean producerOnly = eipName != null && PRODUCER_EIPS.contains(eipName);

            // look ahead for a parameters: block at the same indent level as uri
            StringBuilder uriBuilder = new StringBuilder(uri);
            boolean hasParams = uri.contains("?");
            Map<String, Integer> optionLineMap = new LinkedHashMap<>();
            for (int j = i + 1; j < lines.length; j++) {
                String next = lines[j];
                if (next.isBlank()) {
                    continue;
                }
                int nextIndent = countLeadingSpaces(next);
                if (nextIndent < lineIndent) {
                    break;
                }
                String nextTrimmed = next.trim();
                if (nextIndent == lineIndent && nextTrimmed.startsWith("parameters:")) {
                    int paramBlockIndent = nextIndent;
                    for (int k = j + 1; k < lines.length; k++) {
                        String paramLine = lines[k];
                        if (paramLine.isBlank()) {
                            continue;
                        }
                        int paramIndent = countLeadingSpaces(paramLine);
                        if (paramIndent <= paramBlockIndent) {
                            break;
                        }
                        String paramTrimmed = paramLine.trim();
                        int colonPos = paramTrimmed.indexOf(':');
                        if (colonPos > 0) {
                            String key = paramTrimmed.substring(0, colonPos).trim();
                            String val = unquote(paramTrimmed.substring(colonPos + 1).trim());
                            char sep = hasParams ? '&' : '?';
                            uriBuilder.append(sep).append(key).append('=').append(val);
                            hasParams = true;
                            optionLineMap.put(key, k);
                        }
                    }
                    break;
                }
                if (nextIndent == lineIndent) {
                    break;
                }
            }

            String fullUri = uriBuilder.toString();
            try {
                EndpointValidationResult result
                        = catalog.validateEndpointProperties(fullUri, false, consumerOnly, producerOnly);
                if (!result.isSuccess()) {
                    String scheme = fullUri.contains(":") ? fullUri.substring(0, fullUri.indexOf(':')) : fullUri;
                    collectEndpointErrors(errors, result, scheme, i, optionLineMap);
                }
                checkRegexOptions(errors, fullUri, i, optionLineMap);
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        return errors;
    }

    /** Options models write that the component does not have, and what the component does instead. */
    private static final Map<String, String> INVENTED_OPTIONS = Map.ofEntries(
            Map.entry("file:mkdir", "directories are created by default (autoCreate=true); remove the option"),
            Map.entry("file:createDirectory", "directories are created by default (autoCreate=true); remove the option"),
            Map.entry("file:overwrite", "an existing file is overridden by default (fileExist=Override); remove the option"),
            Map.entry("file:append", "write fileExist=Append"),
            Map.entry("file:name", "write fileName=<name>"),
            Map.entry("file:filename", "write fileName=<name>"),
            Map.entry("file:body",
                    "the file content is the message body: set it with a setBody step before the to: file: step"),
            Map.entry("file:content",
                    "the file content is the message body: set it with a setBody step before the to: file: step"),
            Map.entry("timer:interval", "write period=<millis>"),
            Map.entry("timer:delayMs", "write delay=<millis>"),
            Map.entry("timer:repeat", "write repeatCount=<n>"),
            Map.entry("timer:body", "a timer message has no body: set it with a setBody step (setBody: {constant: \"...\"})"),
            Map.entry("timer:message",
                    "a timer message has no body: set it with a setBody step (setBody: {constant: \"...\"})"),
            Map.entry("timer:cron", "a cron expression is the cron or quartz component: cron:tick?schedule=0/5+*+*+*+*+?"),
            Map.entry("timer:schedule", "a cron expression is the cron or quartz component: cron:tick?schedule=0/5+*+*+*+*+?"),
            Map.entry("log:message", "the message is the body; a text is set with a setBody step or the log EIP"),
            Map.entry("log:name", "the logger name is the path: log:com.example"));

    /** direct:a,direct:b or log:a,mock:b: a comma followed by another scheme inside one uri. */
    private static final Pattern SEVERAL_ENDPOINTS_PATTERN
            = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:[^?]*,[a-zA-Z][a-zA-Z0-9+.-]*:");

    private static final Set<String> FILE_SCHEMES = Set.of("file", "ftp", "ftps", "sftp", "file-watch", "smb");

    /**
     * include and exclude on the file components are regular expressions: include=*.txt fails at startup with a
     * PatternSyntaxException wrapped in a binding error. Says to write .*\\.txt or use antInclude.
     */
    static void checkRegexOptions(List<String> errors, String fullUri, int uriLineIdx, Map<String, Integer> optionLineMap) {
        int colon = fullUri.indexOf(':');
        int q = fullUri.indexOf('?');
        if (colon < 0 || q < 0 || !FILE_SCHEMES.contains(fullUri.substring(0, colon))) {
            return;
        }
        for (String pair : fullUri.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String name = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (!name.equals("include") && !name.equals("exclude") || value.startsWith("{{")) {
                continue;
            }
            try {
                Pattern.compile(value);
            } catch (java.util.regex.PatternSyntaxException e) {
                String ant = name.equals("include") ? "antInclude" : "antExclude";
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx)) + fullUri.substring(0, colon) + ": "
                           + name + "=" + value + " is not a regular expression (" + e.getDescription() + "): " + name
                           + " is a regex, write " + name + "=" + toRegex(value) + ", or use the wildcard option " + ant
                           + "=" + value);
            }
        }
    }

    /** A wildcard such as *.txt as the regex .*\\.txt. */
    private static String toRegex(String wildcard) {
        StringBuilder sb = new StringBuilder();
        for (char ch : wildcard.toCharArray()) {
            if (ch == '*') {
                sb.append(".*");
            } else if (ch == '?') {
                sb.append('.');
            } else if (".\\+()[]{}^$|".indexOf(ch) >= 0) {
                sb.append('\\').append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    static void collectEndpointErrors(
            List<String> errors, EndpointValidationResult result, String scheme,
            int uriLineIdx, Map<String, Integer> optionLineMap) {
        if (result.getUnknown() != null) {
            for (String name : result.getUnknown()) {
                StringBuilder sb = new StringBuilder(scheme).append(": Unknown option '").append(name).append("'");
                if (result.getUnknownSuggestions() != null) {
                    String[] suggestions = result.getUnknownSuggestions().get(name);
                    if (suggestions != null && suggestions.length > 0) {
                        sb.append(". Did you mean: ").append(Arrays.asList(suggestions));
                    }
                }
                String known = INVENTED_OPTIONS.get(scheme + ":" + name);
                if (known != null) {
                    sb.append(" (").append(known).append(")");
                }
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx)) + sb);
            }
        }
        addInvalid(errors, result.getInvalidBoolean(), "boolean", scheme, uriLineIdx, optionLineMap);
        addInvalid(errors, result.getInvalidInteger(), "integer", scheme, uriLineIdx, optionLineMap);
        addInvalid(errors, result.getInvalidNumber(), "number", scheme, uriLineIdx, optionLineMap);
        if (result.getInvalidEnum() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidEnum().entrySet()) {
                StringBuilder sb = new StringBuilder(scheme)
                        .append(": Invalid enum value '").append(entry.getValue())
                        .append("' for option '").append(entry.getKey()).append("'");
                if (result.getInvalidEnumChoices() != null) {
                    String[] choices = result.getInvalidEnumChoices().get(entry.getKey());
                    if (choices != null) {
                        sb.append(". Possible values: ").append(Arrays.asList(choices));
                    }
                }
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx)) + sb);
            }
        }
        if (result.getNotConsumerOnly() != null) {
            for (String name : result.getNotConsumerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in consumer only mode (from: consumes;"
                           + " a producer option belongs on a to:)");
            }
        }
        if (result.getNotProducerOnly() != null) {
            for (String name : result.getNotProducerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in producer only mode (to: sends to the"
                           + " endpoint, for " + scheme + ": it writes the body; to read from an endpoint in the middle of a"
                           + " route use the poll EIP, or pollEnrich, see camel_catalog_sample poll)");
            }
        }
    }

    private static void addInvalid(
            List<String> errors, Map<String, String> invalid, String type, String scheme, int uriLineIdx,
            Map<String, Integer> optionLineMap) {
        if (invalid != null) {
            for (Map.Entry<String, String> entry : invalid.entrySet()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx))
                           + scheme + ": Invalid " + type + " value '" + entry.getValue() + "' for option '"
                           + entry.getKey() + "'");
            }
        }
    }

    static String linePrefix(int lineIdx) {
        return "Line " + (lineIdx + 1) + ": ";
    }

    static String findParentEip(String[] lines, int lineIdx, int lineIndent) {
        int indent = lineIndent;
        for (int j = lineIdx - 1; j >= 0; j--) {
            String prev = lines[j];
            if (prev.isBlank()) {
                continue;
            }
            int prevIndent = countLeadingSpaces(prev);
            if (prevIndent < indent) {
                String eip = extractEipFromLine(prev.trim());
                if ("expression".equals(eip)) {
                    // when: - expression: simple: ...  : the wrapper is not the EIP, keep looking for when
                    indent = prevIndent;
                    continue;
                }
                return eip;
            }
        }
        return null;
    }

    static String extractEipFromLine(String trimmed) {
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            return trimmed.substring(0, colon).trim();
        }
        return null;
    }

    static String extractYamlValue(String trimmed, String key) {
        String prefix = key + ":";
        if (!trimmed.startsWith(prefix)) {
            return null;
        }
        return unquote(trimmed.substring(prefix.length()).trim());
    }

    static String unquote(String val) {
        if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }
        if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }

    static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
