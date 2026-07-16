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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelTableColumns;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.dsl.jbang.core.model.InfraBaseDTO;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

public abstract class InfraBaseCommand extends CamelCommand {

    protected final ObjectMapper jsonMapper = new ObjectMapper();

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    protected InfraBaseCommand(CamelJBangMain main) {
        super(main);

        jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        jsonMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        jsonMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        jsonMapper.configure(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_OPTIONALS, false);
    }

    protected static Map<Long, Path> findPids(String name) throws Exception {
        Map<Long, Path> pids = new HashMap<>();

        // we need to know the pids of the running camel integrations
        if (!name.matches("\\d+")) {
            if (name.endsWith("!")) {
                // exclusive this name only
                name = name.substring(0, name.length() - 1);
            } else if (!name.endsWith("*")) {
                // lets be open and match all that starts with this pattern
                name = name + "*";
            }
        }

        final String pattern = name;

        try (Stream<Path> files = Files.list(CommandLineHelper.getCamelDir())) {
            List<Path> pidFiles = files.filter(p -> {
                var n = p.getFileName().toString();
                return n.startsWith("infra-") && n.endsWith(".json");
            })
                    .toList();
            for (Path pidFile : pidFiles) {
                String fn = pidFile.getFileName().toString();
                String sn = serviceNameFromPidFile(fn);
                String pid = pidFromPidFile(fn);
                if (pid.equals(pattern) || PatternHelper.matchPattern(sn, pattern)) {
                    pids.put(Long.valueOf(pid), pidFile);
                }
            }
        } catch (NoSuchFileException e) {
            // camel directory does not exist yet
        }

        return pids;
    }

    /**
     * Extracts the service name from an {@code infra-<service>-<pid>.json} pid file name. The service name may itself
     * contain hyphens (for example {@code hive-mq}), so it spans everything between the first and the last hyphen
     * rather than just the second hyphen-delimited segment.
     *
     * @param  pidFileName the pid file name, such as {@code infra-hive-mq-1234.json}.
     * @return             the service name, such as {@code hive-mq}.
     */
    protected static String serviceNameFromPidFile(String pidFileName) {
        return pidFileName.substring(pidFileName.indexOf('-') + 1, pidFileName.lastIndexOf('-'));
    }

    /**
     * Extracts the pid from an {@code infra-<service>-<pid>.json} pid file name. The pid is the segment between the
     * last hyphen and the file extension, which is robust to service names that themselves contain hyphens.
     *
     * @param  pidFileName the pid file name, such as {@code infra-hive-mq-1234.json}.
     * @return             the pid, such as {@code 1234}.
     */
    protected static String pidFromPidFile(String pidFileName) {
        return pidFileName.substring(pidFileName.lastIndexOf('-') + 1, pidFileName.lastIndexOf('.'));
    }

    protected boolean showPidColumn() {
        return false;
    }

    protected List<TestInfraService> getMetadata() throws IOException {
        List<TestInfraService> metadata;

        CamelCatalog catalog = new DefaultCamelCatalog();
        try (InputStream is
                = catalog.loadResource("test-infra", "metadata.json")) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            metadata = jsonMapper.readValue(json, new TypeReference<>() {
            });
        }

        return metadata;
    }

    public int listServices(Consumer<List<Row>> serviceConsumer) throws IOException {
        Map<String, InfraServiceAlias> services = new LinkedHashMap<>();

        List<TestInfraService> metadata = getMetadata();

        List<InfraList.Row> rows = new ArrayList<>(metadata.size());

        for (TestInfraService service : metadata) {
            for (String alias : service.alias()) {
                if (!services.containsKey(alias)) {
                    services.put(alias, new InfraServiceAlias(service.description()));
                }
                if (service.aliasImplementation() != null) {
                    services.get(alias).getAliasImplementation().addAll(service.aliasImplementation());
                }
                if (service.uiSupported()) {
                    services.get(alias).setUiSupported(true);
                }
            }
        }

        int width = 0;
        for (Map.Entry<String, InfraServiceAlias> entry : services.entrySet()) {
            width = Math.max(width, entry.getKey().length());
            String pid = findPid(entry.getKey());
            rows.add(new InfraList.Row(
                    pid,
                    entry.getKey(),
                    entry.getValue().getAliasImplementation()
                            .stream()
                            .sorted()
                            .collect(Collectors.joining(", ")),
                    entry.getValue().getDescription(),
                    getServiceData(entry.getKey(), pid),
                    entry.getValue().isUiSupported()));
        }

        rows.sort(Comparator.comparing(InfraList.Row::alias));

        // Do something with the full services list (for example, filter)
        serviceConsumer.accept(rows);

        if (jsonOutput) {
            printer().println(
                    Jsoner.serialize(
                            rows.stream().map(row -> new InfraBaseDTO(
                                    row.alias, row.aliasImplementation, row.description,
                                    parseServiceData(row.serviceData()), row.uiSupported()))
                                    .map(InfraBaseDTO::toMap)
                                    .collect(Collectors.toList())));
        } else {
            int tw = terminalWidth();
            // Size DESCRIPTION to fill the terminal: measure the other columns so it gets the exact remainder.
            // IMPLEMENTATION is capped and SERVICE_DATA keeps a compact fixed width; both truncate with an ellipsis
            // instead of overflowing the terminal (the full, structured service data is available via --json).
            int serviceDataWidth = 30;
            int uiWidth = 4;
            int aliasWidth = width + 2;
            int pidWidth = showPidColumn()
                    ? CamelTableColumns.measure("PID", Integer.MAX_VALUE, rows, r -> r.pid) : 0;
            int implWidth = CamelTableColumns.measure("IMPLEMENTATION", 35, rows, Row::aliasImplementation);
            int overhead = TerminalWidthHelper.noBorderOverhead(showPidColumn() ? 6 : 5);
            int descWidth = CamelTableColumns.lastColumnWidth(
                    tw, overhead, pidWidth, aliasWidth, implWidth, uiWidth, serviceDataWidth);
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").visible(showPidColumn()).headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("ALIAS").minWidth(aliasWidth).dataAlign(HorizontalAlign.LEFT)
                            .with(Row::alias),
                    new Column().header("IMPLEMENTATION").maxWidth(implWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .dataAlign(HorizontalAlign.LEFT).with(Row::aliasImplementation),
                    new Column().header("UI").maxWidth(uiWidth).dataAlign(HorizontalAlign.CENTER)
                            .with(r -> r.uiSupported() ? "x" : ""),
                    CamelTableColumns.lastText("DESCRIPTION", descWidth).with(Row::description),
                    new Column().header("SERVICE_DATA").maxWidth(serviceDataWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .dataAlign(HorizontalAlign.LEFT).with(Row::serviceData))));
        }

        return 0;
    }

    /**
     * Parses the raw service-data JSON string (read from the infra {@code .json} file) into a structured object so it
     * is emitted as nested JSON by {@code --json}, rather than as an escaped string. Returns {@code null} (so the
     * {@code serviceData} field is omitted) when there is no data or the stored content is not valid JSON.
     */
    static Object parseServiceData(String serviceData) {
        if (serviceData == null) {
            return null;
        }
        try {
            return Jsoner.deserialize(serviceData);
        } catch (DeserializationException e) {
            return null;
        }
    }

    private String getServiceData(String key, String pid) {
        Path jsonFilePath = CommandLineHelper.getCamelDir().resolve(getJsonFileName(key, pid));
        if (jsonFilePath.toFile().exists()) {
            try {
                return Files.readString(jsonFilePath);
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    private String findPid(String key) {
        Path p = CommandLineHelper.getCamelDir();
        try {
            Files.createDirectories(p);
            for (String s : Objects.requireNonNull(p.toFile().list())) {
                if (s.startsWith("infra-" + key + "-") && s.endsWith(".json")) {
                    return pidFromPidFile(s);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public String getLogFileName(String service, String pid) {
        return String.format("infra-%s-%s.log", service, pid);
    }

    public String getJsonFileName(String service, String pid) {
        return String.format("infra-%s-%s.json", service, pid);
    }

    record TestInfraService(
            String service,
            String implementation,
            String description,
            List<String> alias,
            List<String> aliasImplementation,
            String groupId,
            String artifactId,
            String version,
            String serviceVersion,
            boolean uiSupported) {
    }

    record Row(String pid, String alias, String aliasImplementation, String description, String serviceData,
            boolean uiSupported) {
    }

    private static class InfraServiceAlias {
        private final String description;
        private final Set<String> aliasImplementation = new HashSet<>();
        private boolean uiSupported;

        public InfraServiceAlias(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public Set<String> getAliasImplementation() {
            return aliasImplementation;
        }

        public boolean isUiSupported() {
            return uiSupported;
        }

        public void setUiSupported(boolean uiSupported) {
            this.uiSupported = uiSupported;
        }
    }
}
