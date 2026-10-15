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
package org.apache.camel.dsl.jbang.core.commands.validate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import picocli.CommandLine;

@CommandLine.Command(name = "normalize",
                     description = "Normalize YAML routes to canonical (explicit) form")
public class YamlNormalizeCommand extends CamelCommand {

    private static final String IGNORE_FILE = "application";
    // the top-level elements of a file that apply to the routes of that file only
    private static final Set<String> SCOPED = Set.of("onException", "errorHandler", "onCompletion", "intercept",
            "interceptFrom", "interceptSendToEndpoint");

    @CommandLine.Mixin
    MavenResolverMixin mavenResolver;

    // the warnings go to stderr, so that stdout has only the normalized YAML
    PrintStream stderr = System.err;

    @CommandLine.Option(names = { "--output" },
                        description = "File or directory to write normalized output. If not specified, output is printed to console."
                                      + " A directory (an existing one, or a name ending with /) gets one file per input file,"
                                      + " with the name of the input file; input files with the same name are written as"
                                      + " <parent directory>-<name> (or <name>-<n>, with the first number no other file uses,"
                                      + " when that clashes too), and what was not loaded from an input file goes to"
                                      + " normalized.yaml. A file gets the routes of all the input files merged."
                                      + " The output must not overwrite one of the input files.")
    private String output;

    @CommandLine.Parameters(description = { "The Camel YAML source files to normalize." },
                            arity = "1..9",
                            paramLabel = "<files>",
                            parameterConsumer = FilesConsumer.class)
    Path[] filePaths;
    List<String> files = new ArrayList<>();

    public YamlNormalizeCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<String> matched = new ArrayList<>();
        for (String n : files) {
            if (matchFile(n)) {
                matched.add(n);
            }
        }
        if (matched.isEmpty()) {
            return 0;
        }
        // a Kamelet loads as a route template: its template is normalized and put back into the Kamelet file
        Map<String, String> kamelets = new LinkedHashMap<>();
        for (String n : matched) {
            String content = readFile(n);
            if (content != null && KameletNormalizer.isKamelet(content)) {
                kamelets.put(n, content);
            }
        }

        // the output must not replace an input file
        if (output != null) {
            Path outPath = Path.of(output);
            List<Path> targets = new ArrayList<>();
            if (isDirectory(outPath)) {
                Map<Path, String> names = outputNames(matched);
                for (String name : names.values()) {
                    targets.add(outPath.resolve(name));
                }
                targets.add(outPath.resolve(unattributedName(names.values())));
            } else {
                targets.add(outPath);
            }
            for (Path t : targets) {
                String input = inputAt(t, matched);
                if (input != null) {
                    printer().printErr("--output would overwrite the input file " + input + " with " + t
                                       + ": write the normalized files to another file or directory");
                    return 1;
                }
            }
        }

        // a work directory of its own, outside the one of camel run (which run empties when it starts), so that two
        // normalizations at the same time do not mix their dumps
        Path workDir = Files.createTempDirectory("camel-normalize-");
        try {
            return normalize(matched, kamelets, workDir);
        } finally {
            FileUtil.removeDir(workDir.toFile());
        }
    }

    private Integer normalize(List<String> matched, Map<String, String> kamelets, Path workDir) throws Exception {
        // one file per input file (the top-level onException of a file applies to the routes of that file only),
        // and all the routes merged into one file
        Map<Path, String> names = outputNames(matched);
        String unattributed = unattributedName(names.values());
        Path perFileDir = workDir.resolve("files");
        Path target = workDir.resolve("normalized.yaml");
        NormalizeDumpRoutesStrategy strategy = new NormalizeDumpRoutesStrategy(perFileDir, target, names, unattributed);

        Run run = new Run(getMain()) {
            @Override
            protected void doAddInitialProperty(KameletMain main) {
                main.addInitialProperty("camel.main.dumpRoutes", "yaml");
                main.addInitialProperty("camel.main.dumpRoutesInclude",
                        "routes,rests,routeConfigurations,routeTemplates,beans,dataFormats");
                main.addInitialProperty("camel.main.dumpRoutesLog", "false");
                main.addInitialProperty("camel.main.dumpRoutesResolvePlaceholders", "false");
                main.addInitialProperty("camel.main.dumpRoutesUriAsParameters", "true");
                main.addInitialProperty("camel.debug.enabled", "false");
                main.addInitialProperty(CamelJBangConstants.TRANSFORM, "true");
                main.addInitialProperty("camel.component.properties.ignoreMissingProperty", "true");
                main.addInitialProperty("camel.language.bean.validate", "false");
                main.addMainListener(new MainListenerSupport() {
                    @Override
                    public void beforeConfigure(BaseMainSupport main) {
                        CamelContext context = main.getCamelContext();
                        strategy.setCamelContext(context);
                        context.getCamelContextExtension().addContextPlugin(DumpRoutesStrategy.class, strategy);
                    }
                });
            }
        };
        run.mavenResolver = mavenResolver;
        run.files = matched;
        run.executionLimitOptions.maxSeconds = 1;
        Integer exit = run.runTransform(true);
        if (exit != null && exit != 0) {
            return exit;
        }

        String normalized = waitForDumpFile(target);
        if (normalized == null) {
            printer().printErr("Error normalizing files: nothing was loaded from " + String.join(", ", matched)
                               + " (see the errors above)");
            return 1;
        }

        // the dump has one item per routeTemplate, route, rest...: those of the Kamelets go back into their files
        Map<String, String> kameletOutput = new LinkedHashMap<>();
        Set<String> kameletIds = new HashSet<>();
        for (Map.Entry<String, String> e : kamelets.entrySet()) {
            String id = KameletNormalizer.kameletName(e.getValue(), e.getKey());
            List<String> lost = new ArrayList<>();
            String k = KameletNormalizer.normalize(e.getValue(), normalized, id, lost);
            if (!lost.isEmpty()) {
                printer().printErr("WARN: " + e.getKey() + ": " + lost.size() + " comment line(s) of the template could"
                                   + " not be put back where they were, they are at the end of the template: "
                                   + String.join(" / ", lost));
            }
            if (k == null) {
                printer().printErr("Error normalizing the Kamelet " + e.getKey() + ": its template " + id
                                   + " did not load (see the errors above), or the file has no spec.template");
                return 1;
            }
            kameletIds.add(id);
            kameletOutput.put(e.getKey(), k);
        }
        String routes = KameletNormalizer.withoutTemplates(normalized, kameletIds);

        // what was dumped for each input file that is not a Kamelet
        Map<String, String> perFile = new LinkedHashMap<>();
        for (String n : matched) {
            if (!kamelets.containsKey(n)) {
                String text = readFile(perFileDir.resolve(names.get(sourcePath(n))).toString());
                if (text != null) {
                    perFile.put(n, KameletNormalizer.withoutTemplates(text, kameletIds));
                }
            }
        }

        Path outPath = output != null ? Path.of(output) : null;
        if (outPath != null && isDirectory(outPath)) {
            Files.createDirectories(outPath);
            for (Map.Entry<String, String> e : perFile.entrySet()) {
                if (!e.getValue().isBlank()) {
                    Files.writeString(outPath.resolve(names.get(sourcePath(e.getKey()))), e.getValue());
                }
            }
            for (Map.Entry<String, String> e : kameletOutput.entrySet()) {
                Files.writeString(outPath.resolve(names.get(sourcePath(e.getKey()))), e.getValue());
            }
            // what was not loaded from one of the input files
            String other = readFile(perFileDir.resolve(unattributed).toString());
            if (other != null) {
                other = KameletNormalizer.withoutTemplates(other, kameletIds);
                if (!other.isBlank()) {
                    Files.writeString(outPath.resolve(unattributed), other);
                }
            }
            printer().println("Normalized " + matched.size() + " file(s) to " + output);
            return 0;
        }

        // the routes of all the files are written together: a top-level onException (and the like) of one file then
        // applies to the routes of the other files
        warnIfScopeWidens(perFile);
        List<String> documents = new ArrayList<>();
        if (!routes.isBlank()) {
            documents.add(routes);
        }
        documents.addAll(kameletOutput.values());
        if (outPath != null) {
            if (documents.size() > 1 && !kameletOutput.isEmpty()) {
                printer().printErr("--output must be a directory to normalize Kamelets with other files:"
                                   + " each Kamelet is written to a file of its own name");
                return 1;
            }
            Files.writeString(outPath, documents.get(0));
            printer().println("Normalized " + matched.size() + " file(s) to " + output);
        } else {
            // more than one document: one YAML stream, separated by ---
            printer().println(String.join("\n---\n", documents));
        }

        return 0;
    }

    /**
     * Whether the output is a directory: an existing one, or a name ending with /
     */
    private boolean isDirectory(Path outPath) {
        return Files.isDirectory(outPath) || output.endsWith("/") || output.endsWith(File.separator);
    }

    /**
     * Warns when the top-level onException, errorHandler, onCompletion or intercept of an input file ends up applying
     * to the routes of the other input files, as the routes of all the files are written together.
     */
    private void warnIfScopeWidens(Map<String, String> perFile) {
        Set<String> withRoutes = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : perFile.entrySet()) {
            if (!topLevel(e.getValue(), Set.of("route")).isEmpty()) {
                withRoutes.add(e.getKey());
            }
        }
        for (Map.Entry<String, String> e : perFile.entrySet()) {
            Set<String> kinds = topLevel(e.getValue(), SCOPED);
            Set<String> others = new LinkedHashSet<>(withRoutes);
            others.remove(e.getKey());
            if (!kinds.isEmpty() && !others.isEmpty()) {
                // to stderr: without --output the normalized YAML is printed to stdout
                stderr.println("WARN: the top-level " + String.join(", ", kinds) + " of " + e.getKey()
                               + " applies to the routes of " + String.join(", ", others)
                               + " too, as the routes of all the files are written together:"
                               + " use --output with a directory to write one file per input file");
            }
        }
    }

    /**
     * The kinds of the top-level elements of a YAML dump, among those given
     */
    static Set<String> topLevel(String yaml, Set<String> kinds) {
        Set<String> answer = new LinkedHashSet<>();
        for (String line : yaml.split("\n")) {
            if (line.startsWith("- ") && line.endsWith(":")) {
                String kind = line.substring(2, line.length() - 1).trim();
                if (kinds.contains(kind)) {
                    answer.add(kind);
                }
            }
        }
        return answer;
    }

    /**
     * The file name of each input file in an output directory: its own name, or, when several input files have the same
     * name, the name of their parent directory, a dash and their name ({@code orders-routes.yaml}), or, when these
     * clash too, their name with a number before the extension ({@code routes-1.yaml}, {@code action-1.kamelet.yaml},
     * as Camel picks the loader of a file by all its extensions). Every name is allocated against the names of all the
     * input files and the names already allocated, so no two input files get the same name and no generated name is the
     * name of another input file.
     */
    static Map<Path, String> outputNames(List<String> files) {
        Map<String, List<Path>> byName = new LinkedHashMap<>();
        for (String f : files) {
            Path p = sourcePath(f);
            List<Path> same = byName.computeIfAbsent(p.getFileName().toString(), k -> new ArrayList<>());
            if (!same.contains(p)) {
                same.add(p);
            }
        }
        // the names of the input files are reserved: a generated name must not be one of them
        Set<String> taken = new HashSet<>(byName.keySet());
        Map<Path, String> answer = new LinkedHashMap<>();
        for (Map.Entry<String, List<Path>> e : byName.entrySet()) {
            if (e.getValue().size() == 1) {
                answer.put(e.getValue().get(0), e.getKey());
            }
        }
        for (Map.Entry<String, List<Path>> e : byName.entrySet()) {
            String name = e.getKey();
            List<Path> same = e.getValue();
            if (same.size() == 1) {
                continue;
            }
            Set<String> prefixed = new HashSet<>();
            for (Path p : same) {
                prefixed.add(withParent(p));
            }
            boolean unique = prefixed.size() == same.size() && prefixed.stream().noneMatch(taken::contains);
            int n = 1;
            for (Path p : same) {
                String answerName;
                if (unique) {
                    answerName = withParent(p);
                } else {
                    do {
                        answerName = FileUtil.onlyName(name, false) + "-" + n++ + "." + FileUtil.onlyExt(name, false);
                    } while (taken.contains(answerName));
                }
                taken.add(answerName);
                answer.put(p, answerName);
            }
        }
        return answer;
    }

    private static String withParent(Path p) {
        Path parent = p.getParent() != null ? p.getParent().getFileName() : null;
        return parent != null ? parent + "-" + p.getFileName() : p.getFileName().toString();
    }

    /**
     * The file name in an output directory for what was not loaded from one of the input files
     */
    static String unattributedName(Collection<String> names) {
        String answer = "normalized.yaml";
        for (int i = 1; names.contains(answer); i++) {
            answer = "normalized-" + i + ".yaml";
        }
        return answer;
    }

    /**
     * The input file that a path of the output is, or null when it is none of them
     */
    private static String inputAt(Path target, List<String> inputs) {
        if (!Files.exists(target)) {
            return null;
        }
        for (String n : inputs) {
            Path input = sourcePath(n);
            try {
                if (Files.exists(input) && Files.isSameFile(target, input)) {
                    return n;
                }
            } catch (IOException e) {
                // not the same file
            }
        }
        return null;
    }

    private static Path sourcePath(String name) {
        String n = name.startsWith("file:") ? name.substring(5) : name;
        return Path.of(n).toAbsolutePath().normalize();
    }

    private static String readFile(String name) {
        try {
            String n = name.startsWith("file:") ? name.substring(5) : name;
            Path p = Path.of(n);
            return Files.isRegularFile(p) ? Files.readString(p) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String waitForDumpFile(Path dumpFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                Thread.sleep(100);

                if (Files.exists(dumpFile)) {
                    try (InputStream is = Files.newInputStream(dumpFile)) {
                        return IOHelper.loadText(is);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private static boolean matchFile(String name) {
        String no = FileUtil.onlyName(name, true).toLowerCase(Locale.ROOT);
        if (IGNORE_FILE.equals(no)) {
            return false;
        }
        String ext = FileUtil.onlyExt(name, true);
        if (ext == null) {
            return false;
        }
        ext = ext.toLowerCase(Locale.ROOT);
        return "yml".equals(ext) || "yaml".equals(ext);
    }

    static class FilesConsumer extends CamelCommand.ParameterConsumer<YamlNormalizeCommand> {
        @Override
        protected void doConsumeParameters(Stack<String> args, YamlNormalizeCommand cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }
}
