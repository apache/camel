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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.networknt.schema.Error;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.yaml.validator.YamlValidator;

import static org.apache.camel.dsl.jbang.core.commands.ai.JavaChecks.JAVA_CLASS_PATTERN;
import static org.apache.camel.dsl.jbang.core.commands.ai.JavaChecks.JAVA_PACKAGE_PATTERN;
import static org.apache.camel.dsl.jbang.core.commands.ai.JavaChecks.publicMethodNames;
import static org.apache.camel.dsl.jbang.core.commands.ai.JavaChecks.withSiblingClassHints;

/**
 * Validates integration source files the way the Camel TUI's editor does on save, before an AI agent writes them: Camel
 * YAML DSL against the YAML DSL schema (unknown or misspelled options, wrong structure), then the endpoint URIs and
 * simple expressions in it against the catalog; a .properties file line by line against the catalog of {@code camel.*}
 * options. Every message names the line so the agent can fix the file.
 */
public final class SourceValidator {

    private static final String BUILTIN_VERSION = new DefaultCamelCatalog().getCatalogVersion();
    private static volatile YamlValidator yamlValidator;
    /** The schema validators of other Camel versions, by version (CAMEL-24711). */
    private static final Map<String, YamlValidator> VERSION_VALIDATORS = new ConcurrentHashMap<>();

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
     * @param  catalog           the catalog of the Camel version the source is for (required: every check that is not
     *                           the schema reads it)
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
        return validate(fileName, content, catalog, extraPropertyLine, directory, null);
    }

    /**
     * As {@link #validate(String, String, CamelCatalog, Function, Path)}, with the YAML DSL schema validator to use:
     * one built for the schema of another Camel version, or null for the schema of the catalog's version.
     */
    public static List<String> validate(
            String fileName, String content, CamelCatalog catalog, Function<String, String> extraPropertyLine,
            Path directory, YamlValidator schemaValidator) {
        Objects.requireNonNull(catalog, "catalog");
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            // a rest binding to an OpenAPI specification hides the verb, and the specification is a file beside the
            // route: read it, so that a GET operation is known to carry no body (CAMEL-24844)
            List<String> msgs = validateCamelYaml(content, catalog, schemaValidator,
                    directory != null ? OpenApiVerbs.bodylessEndpoints(content, directory) : Set.of());
            if (directory != null && msgs.isEmpty()) {
                msgs = new ArrayList<>(msgs);
                BeanDeclarations declarations = BeanDeclarations.scan(directory, fileName);
                msgs.addAll(validateYamlBeanRefs(content, declarations, catalog));
                msgs.addAll(validateResourceRefs(content, directory));
                msgs.addAll(GroovyImportChecks.validateYamlGroovyImports(content, null, declarations.javaClasses()));
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

    /**
     * Validates Camel YAML DSL source: the YAML DSL schema first, then endpoint URIs and simple expressions against the
     * catalog. The schema is the one of the catalog's Camel version: the CLI's own, or for a catalog of another version
     * the schema read from the {@code camel-yaml-dsl} jar of that version. Returns the messages, empty when the source
     * is valid.
     */
    public static List<String> validateCamelYaml(String content, CamelCatalog catalog) {
        return validateCamelYaml(content, catalog, null);
    }

    /**
     * As {@link #validateCamelYaml(String, CamelCatalog)} with the schema validator to use, null for the one of the
     * catalog's version.
     */
    public static List<String> validateCamelYaml(String content, CamelCatalog catalog, YamlValidator schemaValidator) {
        return validateCamelYaml(content, catalog, schemaValidator, Set.of());
    }

    /**
     * As {@link #validateCamelYaml(String, CamelCatalog, YamlValidator)} with the endpoints known to deliver no body,
     * such as the {@code direct:} endpoint of a GET operation of an OpenAPI specification the file binds to.
     */
    public static List<String> validateCamelYaml(
            String content, CamelCatalog catalog, YamlValidator schemaValidator, Set<String> bodylessEndpoints) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        if (validateYamlSchema(content, catalog, schemaValidator, bodylessEndpoints, msgs)) {
            msgs.addAll(validateYamlCatalog(content, catalog));
        }
        return msgs;
    }

    /**
     * The schema half of {@link #validateCamelYaml(String, CamelCatalog)}: the YAML DSL schema of the catalog's Camel
     * version, without the catalog checks. For a sample that is right for its version but uses what the catalog cannot
     * know (a custom step, a header a component sets at runtime).
     */
    public static List<String> validateYamlSchema(String content, CamelCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        List<String> msgs = new ArrayList<>();
        if (content != null && !content.isBlank()) {
            validateYamlSchema(content, catalog, null, msgs);
        }
        return msgs;
    }

    /** Adds the schema errors to msgs; false when the YAML could not be checked at all (no schema, not YAML). */
    private static boolean validateYamlSchema(
            String content, CamelCatalog catalog, YamlValidator schemaValidator, List<String> msgs) {
        return validateYamlSchema(content, catalog, schemaValidator, Set.of(), msgs);
    }

    private static boolean validateYamlSchema(
            String content, CamelCatalog catalog, YamlValidator schemaValidator, Set<String> bodylessEndpoints,
            List<String> msgs) {
        YamlValidator validator;
        try {
            validator = schemaValidator != null ? schemaValidator : yamlValidator(catalog);
        } catch (Exception e) {
            msgs.add("Cannot validate against the YAML DSL schema of Camel " + catalog.getCatalogVersion() + ": "
                     + e.getMessage());
            return false;
        }
        try {
            msgs.addAll(formatSchemaErrors(validator.validate(content, bodylessEndpoints)));
            return true;
        } catch (Exception e) {
            msgs.add("Invalid YAML: " + e.getMessage());
            return false;
        }
    }

    /**
     * The schema validator of the catalog's Camel version: the CLI's own when the catalog is the built-in one, else one
     * for the schema of the version the catalog was loaded for (CAMEL-24711), built once per version.
     */
    static YamlValidator yamlValidator(CamelCatalog catalog) throws Exception {
        String version = catalog.getCatalogVersion();
        if (version == null || version.equals(BUILTIN_VERSION)) {
            return yamlValidator();
        }
        YamlValidator v = VERSION_VALIDATORS.get(version);
        if (v == null) {
            synchronized (VERSION_VALIDATORS) {
                v = VERSION_VALIDATORS.get(version);
                if (v == null) {
                    String schema = CatalogLoader.loadYamlDslSchema(null, version, false, true);
                    if (schema == null) {
                        return yamlValidator();
                    }
                    v = new YamlValidator(false, schema, catalog);
                    v.init();
                    VERSION_VALIDATORS.put(version, v);
                }
            }
        }
        return v;
    }

    /**
     * The catalog checks of a YAML route without the schema: endpoint URIs and Simple expressions. For a caller that
     * has already validated the schema (the CLI, the schema tool).
     */
    public static List<String> validateYamlCatalog(String content, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        msgs.addAll(StructureChecks.validateTopLevelOrder(content));
        msgs.addAll(validateYamlEndpoints(content, catalog));
        msgs.addAll(validateYamlSimple(content, catalog));
        msgs.addAll(BeanRefChecks.validateReturnedResourceLiterals(content));
        msgs.addAll(JsonPathChecks.validateYamlJsonPath(content, catalog));
        msgs.addAll(validateKnownHeaders(content, catalog));
        msgs.addAll(validateBeanTypes(content));
        return msgs;
    }

    static YamlValidator yamlValidator() throws Exception {
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

    /**
     * Compiles the Java source in memory with the JDK compiler against the classpath of the running CLI, so a bean or
     * processor that would fail when {@code camel run} compiles it is reported first, one message per diagnostic with
     * its line. Returns nothing when no compiler is available (a JRE).
     */
    public static List<String> validateJava(String fileName, String content) {
        return JavaChecks.validateJava(fileName, content);
    }

    /**
     * Compiles the stylesheet the way the xslt components do: with Saxon when it is on the classpath (preferred, XSLT
     * 3.0), otherwise with the JDK processor (XSLT 1.0). A stylesheet that declares version 2.0 or 3.0 when only the
     * JDK processor is available is checked for well-formed XML only, since it is meant for the xslt-saxon component.
     */
    public static List<String> validateXslt(String content) {
        return XmlChecks.validateXslt(content);
    }

    /** Checks that the XML is well formed (an input file, a Camel XML DSL file or any other XML). */
    public static List<String> validateXml(String content) {
        return XmlChecks.validateXml(content);
    }

    /**
     * A Camel* header that no component used in the file defines (CamelTimerIndex; the timer sets CamelTimerCounter):
     * the value is null at runtime. Checked against the header metadata of every component the file names, with the
     * closest real name.
     */
    public static List<String> validateKnownHeaders(String content, CamelCatalog catalog) {
        return HeaderChecks.validateKnownHeaders(content, catalog);
    }

    /**
     * How each bean under {@code beans:} is created, for the classes the validator can load: the properties of a class
     * created through its builder are checked against what the builder accepts, and a class with no public no-arg
     * constructor and no builder is reported with the ways it can be created (CAMEL-24820).
     */
    public static List<String> validateBeanTypes(String content) {
        return BeanTypeChecks.validateBeanTypes(content);
    }

    /** The bean names declared under {@code beans:} in the YAML content. */
    public static Set<String> declaredBeans(String content) {
        return BeanRefChecks.declaredBeans(content);
    }

    /**
     * Bean references in the YAML that nothing declares, each with how to declare it; a bean whose option needs a Camel
     * interface (an aggregationStrategy, an onPrepare processor...) must implement it.
     */
    public static List<String> validateYamlBeanRefs(String content, BeanDeclarations external, CamelCatalog catalog) {
        return BeanRefChecks.validateYamlBeanRefs(content, external, catalog);
    }

    /**
     * Validates a properties file line by line: {@code camel.*} keys against the catalog, the rest with the extra
     * check.
     */
    public static List<String> validateProperties(
            String content, CamelCatalog catalog, Function<String, String> extraPropertyLine) {
        return PropertiesChecks.validateProperties(content, catalog, extraPropertyLine);
    }

    public static String validatePropertyLine(String line, CamelCatalog catalog, Function<String, String> extra) {
        return PropertiesChecks.validatePropertyLine(line, catalog, extra);
    }

    /** Runs a line validator over the key=value lines of a properties file, prefixing each message with its line. */
    public static List<String> validatePropertiesLines(String content, Function<String, String> lineValidator) {
        return PropertiesChecks.validatePropertiesLines(content, lineValidator);
    }

    /** The simple expressions of a YAML route checked against the catalog, as predicate where the EIP expects one. */
    public static List<String> validateYamlSimple(String content, CamelCatalog catalog) {
        return SimpleChecks.validateYamlSimple(content, catalog);
    }

    /**
     * An xslt:stylesheets/x.xsl (or velocity, freemarker...) whose file is not in the directory fails when the route
     * starts; with camel run the files next to the route are on the classpath root, so the endpoint names the file
     * without a folder. Says what is missing and, when a file of that name is in the directory, the endpoint to write.
     */
    public static List<String> validateResourceRefs(String content, Path directory) {
        return BeanRefChecks.validateResourceRefs(content, directory);
    }

    public static List<String> validateYamlEndpoints(String content, CamelCatalog catalog) {
        return EndpointChecks.validateYamlEndpoints(content, catalog);
    }

}
