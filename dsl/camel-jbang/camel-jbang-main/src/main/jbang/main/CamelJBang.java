///usr/bin/env jbang "$0" "$@" ; exit $?

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//REPOS mavencentral,apache=https://repository.apache.org/snapshots
//DEPS org.apache.camel:camel-bom:${camel.jbang.version}@pom
//DEPS org.apache.camel:camel-core
//DEPS org.apache.camel:camel-core-model
//DEPS org.apache.camel:camel-api
//DEPS org.apache.camel:camel-main
//DEPS org.apache.camel:camel-kamelet-main
//DEPS org.apache.camel:camel-file-watch
//DEPS org.apache.camel:camel-resourceresolver-github
//DEPS org.apache.camel:camel-jbang-core:${camel.jbang.version}
//DEPS org.apache.logging.log4j:log4j-api:${camel.jbang.log4j2.version}
//DEPS org.apache.logging.log4j:log4j-core:${camel.jbang.log4j2.version}
//DEPS org.apache.logging.log4j:log4j-slf4j-impl:${camel.jbang.log4j2.version}
//DEPS info.picocli:picocli:${camel.jbang.picocli.version}

package main;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.jbang.core.commands.AbstractSearch;
import org.apache.camel.dsl.jbang.core.common.MatchExtractor;
import org.apache.camel.dsl.jbang.core.components.ComponentConverter;
import org.apache.camel.dsl.jbang.core.components.ComponentDescriptionMatching;
import org.apache.camel.dsl.jbang.core.components.ComponentPrinter;
import org.apache.camel.dsl.jbang.core.kamelets.KameletConverter;
import org.apache.camel.dsl.jbang.core.kamelets.KameletDescriptionMatching;
import org.apache.camel.dsl.jbang.core.kamelets.KameletPrinter;
import org.apache.camel.dsl.jbang.core.languages.LanguageConverter;
import org.apache.camel.dsl.jbang.core.languages.LanguageDescriptionMatching;
import org.apache.camel.dsl.jbang.core.languages.LanguagePrinter;
import org.apache.camel.dsl.jbang.core.others.OtherConverter;
import org.apache.camel.dsl.jbang.core.others.OtherDescriptionMatching;
import org.apache.camel.dsl.jbang.core.others.OtherPrinter;
import org.apache.camel.dsl.jbang.core.types.Component;
import org.apache.camel.dsl.jbang.core.types.Kamelet;
import org.apache.camel.dsl.jbang.core.types.Language;
import org.apache.camel.dsl.jbang.core.types.Other;
import org.apache.camel.main.KameletMain;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "run", description = "Run a Kamelet")
class Run implements Callable<Integer> {
    private CamelContext context;

    @Parameters(description = "The path to the kamelet binding", arity = "1")
    private String binding;

    @Option(names = { "--debug-level" }, defaultValue = "info", description = "Default debug level")
    private String debugLevel;

    class ShutdownRoute extends RouteBuilder {
        private File lockFile;

        public ShutdownRoute(File lockFile) {
            this.lockFile = lockFile;
        }

        public void configure() {
            fromF("file-watch://%s?events=DELETE&antInclude=%s", lockFile.getParent(), lockFile.getName())
                    .process(p -> context.shutdown());
        }
    }

    @Override
    public Integer call() throws Exception {
        switch (debugLevel) {
            case "trace":
                Configurator.setRootLevel(Level.TRACE);
                break;
            case "debug":
                Configurator.setRootLevel(Level.DEBUG);
                break;
            case "info":
                Configurator.setRootLevel(Level.INFO);
                break;
            case "warn":
                Configurator.setRootLevel(Level.WARN);
                break;
            case "fatal":
                Configurator.setRootLevel(Level.FATAL);
                break;
            default: {
                System.err.println("Invalid debug level " + debugLevel);
                return 1;
            }
        }

        File bindingFile = new File(binding);
        if (!bindingFile.exists()) {
            System.err.println("The binding file does not exist");

            return 1;
        }

        System.setProperty("camel.main.routes-include-pattern", "file:" + binding);
        System.setProperty("camel.main.name", "CamelJBang");

        System.out.println("Starting Camel JBang!");
        KameletMain main = new KameletMain();

        main.configure().addRoutesBuilder(new ShutdownRoute(createLockFile()));
        main.start();
        context = main.getCamelContext();

        main.run();
        return 0;
    }

    public File createLockFile() throws IOException {
        File lockFile = File.createTempFile(".run", ".camel.lock", new File("."));

        System.out.printf("A new lock file was created on %s. Delete this file to stop running%n",
                lockFile.getAbsolutePath());
        lockFile.deleteOnExit();

        return lockFile;
    }
}

@Command(name = "search", description = "Search for kameletes, components and patterns (use --help)")
class Search extends AbstractSearch implements Callable<Integer> {
    public Search() {
        super(null, null);
    }

    public void printHeader() {
    }

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(this, System.out);

        return 0;
    }
}

@Command(name = "kamelets", description = "Search for a Kamelet in the Kamelet catalog")
class SearchKamelets extends AbstractSearch implements Callable<Integer> {
    /*
     * Matches the following line. Separate them into groups and pick the last
     * which contains the description:
     *
     * xref:ROOT:mariadb-sink.adoc[image:kamelets/mariadb-sink.svg[] MariaDB Sink]
     */
    private static final Pattern PATTERN = Pattern.compile("(.*):(.*):(.*)\\[(.*)\\[\\] (.*)\\]");

    @Option(names = { "--search-term" }, defaultValue = "", description = "Default debug level")
    private String searchTerm;

    SearchKamelets() {
        super("github:apache:camel-kamelets:docs/modules/ROOT/nav.adoc", PATTERN);
    }

    @Override
    public void printHeader() {
        System.out.printf("%-35s %-45s %s%n", "KAMELET", "DESCRIPTION", "LINK");
        System.out.printf("%-35s %-45s %s%n", "-------", "-----------", "-----");
    }

    @Override
    public Integer call() throws Exception {
        MatchExtractor<Kamelet> matchExtractor;

        if (searchTerm.isEmpty()) {
            matchExtractor = new MatchExtractor<>(PATTERN, new KameletConverter(), new KameletPrinter());

            search(matchExtractor);
        } else {
            matchExtractor = new MatchExtractor<>(
                    PATTERN, new KameletConverter(),
                    new KameletDescriptionMatching(searchTerm));

            search(matchExtractor);
        }

        return 0;
    }
}

@Command(name = "components", description = "Search for Camel Core components")
class SearchComponents extends AbstractSearch implements Callable<Integer> {
    /*
     * Matches the following line. Separate them into groups and pick the last
     * which contains the description:
     *
     * * xref:ROOT:index.adoc[Components]
     */
    private static final Pattern PATTERN = Pattern.compile("(.*):(.*)\\[(.*)\\]");

    @Option(names = { "--search-term" }, defaultValue = "", description = "Default debug level")
    private String searchTerm;

    SearchComponents() {
        super("github:apache:camel:docs/components/modules/ROOT/nav.adoc", PATTERN);
    }

    @Override
    public void printHeader() {
        System.out.printf("%-35s %-45s %s%n", "COMPONENT", "DESCRIPTION", "LINK");
        System.out.printf("%-35s %-45s %s%n", "-------", "-----------", "-----");
    }

    @Override
    public Integer call() throws Exception {

        MatchExtractor<Component> matchExtractor;
        if (searchTerm.isEmpty()) {
            matchExtractor = new MatchExtractor<>(PATTERN, new ComponentConverter(), new ComponentPrinter());

        } else {
            matchExtractor = new MatchExtractor<>(
                    PATTERN, new ComponentConverter(),
                    new ComponentDescriptionMatching(searchTerm));

        }
        search(matchExtractor);

        return 0;
    }
}

@Command(name = "languages", description = "Search for Camel expression languages")
class SearchLanguages extends AbstractSearch implements Callable<Integer> {
    /*
     * Matches the following line. Separate them into groups and pick the last
     * which contains the description:
     *
     * * xref:ROOT:index.adoc[Components]
     */
    private static final Pattern PATTERN = Pattern.compile("(.*):(.*)\\[(.*)\\]");

    @Option(names = { "--search-term" }, defaultValue = "", description = "Default debug level")
    private String searchTerm;

    SearchLanguages() {
        super("github:apache:camel:docs/components/modules/languages/nav.adoc", PATTERN);
    }

    @Override
    public void printHeader() {
        System.out.printf("%-35s %-45s %s%n", "LANGUAGE", "DESCRIPTION", "LINK");
        System.out.printf("%-35s %-45s %s%n", "-------", "-----------", "-----");
    }

    @Override
    public Integer call() throws Exception {

        MatchExtractor<Language> matchExtractor;
        if (searchTerm.isEmpty()) {
            matchExtractor = new MatchExtractor<>(PATTERN, new LanguageConverter(), new LanguagePrinter());

        } else {
            matchExtractor = new MatchExtractor<>(
                    PATTERN, new LanguageConverter(),
                    new LanguageDescriptionMatching(searchTerm));

        }
        search(matchExtractor);

        return 0;
    }
}

@Command(name = "others", description = "Search for Camel miscellaneous components")
class SearchOthers extends AbstractSearch implements Callable<Integer> {
    /*
     * Matches the following line. Separate them into groups and pick the last
     * which contains the description:
     *
     * * xref:ROOT:index.adoc[Components]
     */
    private static final Pattern PATTERN = Pattern.compile("(.*):(.*)\\[(.*)\\]");

    @Option(names = { "--search-term" }, defaultValue = "", description = "Default debug level")
    private String searchTerm;

    SearchOthers() {
        super("github:apache:camel:docs/components/modules/others/nav.adoc", PATTERN);
    }

    @Override
    public void printHeader() {
        System.out.printf("%-35s %-45s %s%n", "COMPONENT", "DESCRIPTION", "LINK");
        System.out.printf("%-35s %-45s %s%n", "-------", "-----------", "-----");
    }

    @Override
    public Integer call() throws Exception {

        MatchExtractor<Other> matchExtractor;
        if (searchTerm.isEmpty()) {
            matchExtractor = new MatchExtractor<>(PATTERN, new OtherConverter(), new OtherPrinter());

        } else {
            matchExtractor = new MatchExtractor<>(
                    PATTERN, new OtherConverter(),
                    new OtherDescriptionMatching(searchTerm));

        }
        search(matchExtractor);

        return 0;
    }
}

@Command(name = "CamelJBang", mixinStandardHelpOptions = true, version = "CamelJBang ${camel.jbang.version}",
         description = "A JBang-based Camel app for running Kamelets")
class CamelJBang implements Callable<Integer> {
    private static CommandLine commandLine;

    static {
        Configurator.initialize(new DefaultConfiguration());
    }

    public static void main(String... args) {
        commandLine = new CommandLine(new CamelJBang())
                .addSubcommand("run", new Run())
                .addSubcommand("search", new CommandLine(new Search())
                        .addSubcommand("kamelets", new SearchKamelets())
                        .addSubcommand("components", new SearchComponents())
                        .addSubcommand("languages", new SearchLanguages())
                        .addSubcommand("others", new SearchOthers()));

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        commandLine.execute("--help");
        return 0;
    }
}
