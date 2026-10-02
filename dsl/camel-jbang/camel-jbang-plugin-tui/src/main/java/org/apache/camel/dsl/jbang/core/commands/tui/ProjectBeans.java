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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The beans of a project for the Source tab: where each bean name and Java class is declared, so a line of a route that
 * refers to a bean (bean:name, .bean(MyBean.class), ref: name, #class:com.foo.MyBean...) can jump to it, and the quick
 * doc can say where it is. Bean names come from {@code @BindToRegistry}, {@code @Named}, {@code @Component},
 * {@code @Service} and {@code @Bean} in Java, and from the beans of YAML and XML files.
 */
final class ProjectBeans {

    /**
     * Where a bean or class is declared.
     *
     * @param label    the bean name, or the simple name of the class
     * @param type     the class of the bean, when known
     * @param filePath the file it is declared in
     * @param line     the line of the declaration, from 0
     */
    record Location(String label, String type, String filePath, int line) {
    }

    static final ProjectBeans NONE = new ProjectBeans(Map.of(), Map.of());

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|final|abstract|static|sealed)\\s+)*(?:class|record|enum|interface)\\s+(\\w+)");
    private static final Pattern NAMED = Pattern.compile(
            "@(?:[\\w.]+\\.)?(BindToRegistry|Named|Component|Service|Bean)\\b(?:\\s*\\(\\s*(?:(?:value|name)\\s*=\\s*)?\"([^\"]+)\")?");
    private static final Pattern XML_BEAN = Pattern.compile("<bean\\s[^>]*?name=\"([^\"]+)\"(?:[^>]*?type=\"([^\"]+)\")?");
    private static final Pattern YAML_NAME = Pattern.compile("^\\s*-?\\s*name:\\s*[\"']?([^\"'\\s#]+)");
    private static final Pattern YAML_TYPE = Pattern.compile("^\\s*type:\\s*[\"']?(?:#class:|#type:)?([\\w.$]+)");

    // what a line of a route refers to
    private static final Pattern REF_CLASS = Pattern.compile("#(?:class|type):([\\w.$]+)");
    private static final Pattern REF_BEAN_URI = Pattern.compile("(?<![\\w.$])bean:([A-Za-z_][\\w-]*)");
    private static final Pattern REF_JAVA_CLASS = Pattern.compile(
            "\\.(?:bean|process)\\(\\s*(?:new\\s+)?(\\w+)\\s*(?:\\.class|\\()");
    private static final Pattern REF_JAVA_NAME = Pattern.compile("\\.(?:bean|process)\\(\\s*\"([^\"]+)\"");
    private static final Pattern REF_YAML
            = Pattern.compile("^\\s*-?\\s*(ref|beanType|aggregationStrategy):\\s*[\"']?([\\w.$-]+)");
    private static final Pattern REF_XML = Pattern.compile("\\s(ref|beanType)=\"([\\w.$-]+)\"");

    private final Map<String, Location> byName;
    private final Map<String, Location> byClass;

    private ProjectBeans(Map<String, Location> byName, Map<String, Location> byClass) {
        this.byName = byName;
        this.byClass = byClass;
    }

    /** The beans the files declare by name, sorted by name. */
    List<Location> beans() {
        List<Location> found = new ArrayList<>(byName.values());
        found.sort(Comparator.comparing(Location::label, String.CASE_INSENSITIVE_ORDER));
        return found;
    }

    /** Reads the beans and classes the files declare. */
    static ProjectBeans scan(List<Path> files) {
        Map<String, Location> byName = new HashMap<>();
        Map<String, Location> byClass = new HashMap<>();
        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                continue;
            }
            if (name.endsWith(".java")) {
                scanJava(file.toString(), lines, byName, byClass);
            } else if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                scanYaml(file.toString(), lines, byName);
            } else if (name.endsWith(".xml")) {
                scanXml(file.toString(), lines, byName);
            }
        }
        return new ProjectBeans(byName, byClass);
    }

    private static void scanJava(
            String filePath, List<String> lines, Map<String, Location> byName, Map<String, Location> byClass) {
        String pkg = "";
        String className = null;
        int classLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = PACKAGE.matcher(lines.get(i));
            if (m.find()) {
                pkg = m.group(1) + ".";
            }
            m = CLASS.matcher(lines.get(i));
            if (m.find()) {
                className = m.group(1);
                classLine = i;
                break;
            }
        }
        if (className == null) {
            return;
        }
        String fqcn = pkg + className;
        Location cls = new Location(className, fqcn, filePath, classLine);
        byClass.putIfAbsent(className, cls);
        byClass.putIfAbsent(fqcn, cls);
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = NAMED.matcher(lines.get(i));
            while (m.find()) {
                boolean onClass = i < classLine;
                String bean = m.group(2);
                if (bean == null && onClass && !"Bean".equals(m.group(1))) {
                    // @BindToRegistry, @Named, @Component on the class: the class name, starting in lower case
                    bean = Character.toLowerCase(className.charAt(0)) + className.substring(1);
                }
                if (bean != null) {
                    byName.putIfAbsent(bean, new Location(bean, onClass ? fqcn : null, filePath, i));
                }
            }
        }
    }

    private static void scanYaml(String filePath, List<String> lines, Map<String, Location> byName) {
        int beansIndent = -1;
        String pending = null;
        int pendingLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (beansIndent >= 0 && indent <= beansIndent) {
                beansIndent = -1;
            }
            if (trimmed.equals("- beans:") || trimmed.equals("beans:")) {
                beansIndent = indent;
                continue;
            }
            if (beansIndent < 0) {
                continue;
            }
            Matcher m = YAML_NAME.matcher(line);
            if (m.find()) {
                pending = m.group(1);
                pendingLine = i;
                byName.putIfAbsent(pending, new Location(pending, null, filePath, i));
                continue;
            }
            m = YAML_TYPE.matcher(line);
            if (m.find() && pending != null) {
                byName.put(pending, new Location(pending, m.group(1), filePath, pendingLine));
                pending = null;
            }
        }
    }

    private static void scanXml(String filePath, List<String> lines, Map<String, Location> byName) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = XML_BEAN.matcher(lines.get(i));
            while (m.find()) {
                String type = m.group(2) != null ? m.group(2).replace("#class:", "") : null;
                byName.putIfAbsent(m.group(1), new Location(m.group(1), type, filePath, i));
            }
        }
    }

    /** The bean or class a line of a route refers to and the project declares, or null. */
    Location refOn(String line) {
        if (line == null || (byName.isEmpty() && byClass.isEmpty())) {
            return null;
        }
        Matcher m = REF_CLASS.matcher(line);
        if (m.find()) {
            return byClass(m.group(1));
        }
        m = REF_JAVA_CLASS.matcher(line);
        if (m.find()) {
            return byClass(m.group(1));
        }
        m = REF_JAVA_NAME.matcher(line);
        if (m.find()) {
            return byName(m.group(1));
        }
        m = REF_BEAN_URI.matcher(line);
        if (m.find()) {
            return byName(m.group(1));
        }
        m = REF_YAML.matcher(line);
        if (!m.find()) {
            m = REF_XML.matcher(line);
            if (!m.find()) {
                return null;
            }
        }
        return "beanType".equals(m.group(1)) ? byClass(m.group(2)) : byName(m.group(2));
    }

    private Location byName(String name) {
        Location l = byName.get(name);
        // a bean named after its class, as a bean without a name is
        return l != null ? l : byClass.get(Character.toUpperCase(name.charAt(0)) + name.substring(1));
    }

    private Location byClass(String name) {
        Location l = byClass.get(name);
        if (l == null && name.contains(".")) {
            l = byClass.get(name.substring(name.lastIndexOf('.') + 1));
        }
        return l;
    }

    /** The quick doc of a bean: what it is and where, and that Enter goes there. */
    static String describe(Location l) {
        String file = Path.of(l.filePath()).getFileName().toString();
        String type = l.type() != null && !l.type().equals(l.label()) ? " (" + l.type() + ")" : "";
        return l.label() + type + " is declared in " + file + ":" + (l.line() + 1) + " — Enter goes there";
    }
}
