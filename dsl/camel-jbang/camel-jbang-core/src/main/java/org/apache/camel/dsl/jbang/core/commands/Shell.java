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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.HomeHelper;
import org.jline.builtins.InteractiveCommandGroup;
import org.jline.builtins.PosixCommandGroup;
import org.jline.picocli.PicocliCommandRegistry;
import org.jline.reader.LineReader;
import org.jline.shell.ShellBuilder;
import org.jline.shell.impl.DefaultAliasManager;
import org.jline.shell.widget.CommandTailTipWidgets;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import picocli.CommandLine;

@CommandLine.Command(name = "shell",
                     description = "Interactive Camel JBang shell.",
                     footer = {
                             "%nExamples:",
                             "  camel shell",
                             "",
                             "Press Ctrl-C to exit." })
public class Shell extends CamelCommand {

    // Camel orange color (packed RGB)
    private static final int CAMEL_ORANGE = 0xF69123;

    // Help colors: title=bold blue, command=38, args=italic, options=yellow, description=dark gray
    private static final String HELP_COLORS = "ti=1;34:co=38:ar=3:op=33:de=90";

    public Shell(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        PicocliCommandRegistry registry = new PicocliCommandRegistry(CamelJBangMain.getCommandLine());

        String homeDir = HomeHelper.resolveHomeDir();
        Path history = Paths.get(homeDir, ".camel-jbang-history");

        // Alias persistence: aliases are stored in ~/.camel-jbang-aliases
        Path aliasFile = Paths.get(homeDir, ".camel-jbang-aliases");
        DefaultAliasManager aliasManager = new DefaultAliasManager(aliasFile);

        // Init script: if ~/.camel-jbang-init exists, it will be executed on shell startup
        Path initScript = Paths.get(homeDir, ".camel-jbang-init");

        String camelVersion = VersionHelper.extractCamelVersion();
        boolean colorEnabled = EnvironmentHelper.isColorEnabled();

        // org.jline.shell.Shell is used via FQCN to avoid clash with this class name
        ShellBuilder builder = org.jline.shell.Shell.builder()
                .prompt(() -> buildPrompt(camelVersion, colorEnabled))
                .rightPrompt(() -> buildRightPrompt(colorEnabled))
                .groups(registry, new PosixCommandGroup(), new InteractiveCommandGroup())
                .historyFile(history)
                .historyCommands(true)
                .helpCommands(true)
                .variableCommands(true)
                .commandHighlighter(true)
                .aliasManager(aliasManager)
                .onReaderReady((reader, dispatcher) -> {
                    // CommandTailTipWidgets provides both fish-style autosuggestion
                    // and command description tooltips below the cursor line
                    new CommandTailTipWidgets(reader, dispatcher, 5).enable();
                })
                .variable(LineReader.LIST_MAX, 50)
                .variable(LineReader.OTHERS_GROUP_NAME, "Others")
                .variable(LineReader.COMPLETION_STYLE_GROUP, "fg:blue,bold")
                .variable("HELP_COLORS", HELP_COLORS)
                .option(LineReader.Option.GROUP_PERSIST, true);

        if (Files.exists(initScript)) {
            builder.initScript(initScript.toFile());
        }

        try (org.jline.shell.Shell shell = builder.build()) {
            printBanner(shell, camelVersion, colorEnabled);
            shell.run();
        }
        return 0;
    }

    private static String buildPrompt(String camelVersion, boolean colorEnabled) {
        if (!colorEnabled) {
            return camelVersion != null ? "camel " + camelVersion + "> " : "camel> ";
        }
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append("camel", AttributedStyle.DEFAULT.bold().foregroundRgb(CAMEL_ORANGE));
        if (camelVersion != null) {
            sb.append(" ");
            sb.append(camelVersion, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
        }
        sb.append("> ", AttributedStyle.DEFAULT);
        return sb.toAnsi();
    }

    private static String buildRightPrompt(boolean colorEnabled) {
        String cwd = System.getProperty("user.dir");
        String home = System.getProperty("user.home");
        if (cwd != null && home != null && cwd.startsWith(home)) {
            cwd = "~" + cwd.substring(home.length());
        }
        if (cwd == null) {
            return null;
        }
        if (!colorEnabled) {
            return cwd;
        }
        return new AttributedStringBuilder()
                .append(cwd, AttributedStyle.DEFAULT.faint())
                .toAnsi();
    }

    private static void printBanner(org.jline.shell.Shell shell, String camelVersion, boolean colorEnabled) {
        var writer = shell.terminal().writer();
        if (colorEnabled) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.append("Apache Camel", AttributedStyle.DEFAULT.bold().foregroundRgb(CAMEL_ORANGE));
            sb.append(" JBang Shell", AttributedStyle.DEFAULT.bold());
            if (camelVersion != null) {
                sb.append(" v" + camelVersion, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
            }
            writer.println(sb.toAnsi(shell.terminal()));
        } else {
            String banner = "Apache Camel JBang Shell";
            if (camelVersion != null) {
                banner += " v" + camelVersion;
            }
            writer.println(banner);
        }
        writer.println("Type 'help' for available commands, 'exit' to quit.");
        writer.println();
        writer.flush();
    }
}
