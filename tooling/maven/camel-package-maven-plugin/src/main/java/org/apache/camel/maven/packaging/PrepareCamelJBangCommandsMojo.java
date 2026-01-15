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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.tooling.model.JBangCommandModel;
import org.apache.camel.tooling.model.JBangCommandModel.JBangCommand;
import org.apache.camel.tooling.model.JBangCommandModel.JBangCommandOption;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;

/**
 * Prepares camel-jbang by scanning command classes and generating command metadata for documentation.
 */
@Mojo(name = "prepare-jbang-commands", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class PrepareCamelJBangCommandsMojo extends AbstractGeneratorMojo {

    // Pattern to match .addSubcommand("name", new CommandLine(new ClassName(main))
    private static final Pattern SUBCOMMAND_PATTERN = Pattern.compile(
            "\\.addSubcommand\\(\\s*\"([^\"]+)\"\\s*,\\s*new\\s+CommandLine\\(\\s*new\\s+([A-Za-z0-9_]+)\\s*\\(\\s*main\\s*\\)\\s*\\)");

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File outFolder;

    @Parameter(defaultValue = "${project.basedir}/src/main/java/org/apache/camel/dsl/jbang/core/commands")
    protected File commandsDir;

    @Inject
    public PrepareCamelJBangCommandsMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute(MavenProject project) throws MojoFailureException, MojoExecutionException {
        outFolder = new File(project.getBasedir(), "src/generated/resources");
        commandsDir = new File(project.getBasedir(), "src/main/java/org/apache/camel/dsl/jbang/core/commands");
        super.execute(project);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!commandsDir.exists() || !commandsDir.isDirectory()) {
            getLog().debug("Commands directory not found: " + commandsDir);
            return;
        }

        try {
            // Step 1: Scan all command classes and build a map by class name
            Map<String, CommandInfo> commandsByClassName = new HashMap<>();
            scanCommandClasses(commandsDir, commandsByClassName);

            // Step 2: Parse CamelJBangMain.java to get command hierarchy
            File mainFile = new File(commandsDir, "CamelJBangMain.java");
            JBangCommandModel model = new JBangCommandModel();

            if (mainFile.exists()) {
                parseCommandHierarchy(mainFile, commandsByClassName, model);
            }

            if (!model.getCommands().isEmpty()) {
                // Sort commands alphabetically
                sortCommandsRecursively(model.getCommands());

                String json = JsonMapper.createJsonSchema(model);
                updateResource(outFolder.toPath(), "META-INF/camel-jbang-commands-metadata.json", json);
                getLog().info("Generated JBang commands metadata with " + countCommands(model) + " commands");
            }
        } catch (Exception e) {
            throw new MojoFailureException("Error generating JBang commands metadata", e);
        }
    }

    private void sortCommandsRecursively(List<JBangCommand> commands) {
        commands.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (JBangCommand cmd : commands) {
            if (cmd.hasSubcommands()) {
                sortCommandsRecursively(cmd.getSubcommands());
            }
        }
    }

    private int countCommands(JBangCommandModel model) {
        int count = model.getCommands().size();
        for (JBangCommand cmd : model.getCommands()) {
            count += countSubcommands(cmd);
        }
        return count;
    }

    private int countSubcommands(JBangCommand cmd) {
        int count = cmd.getSubcommands().size();
        for (JBangCommand sub : cmd.getSubcommands()) {
            count += countSubcommands(sub);
        }
        return count;
    }

    private void scanCommandClasses(File dir, Map<String, CommandInfo> commandsByClassName) throws IOException {
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("CamelJBangMain.java"))
                    .forEach(p -> {
                        try {
                            CommandInfo info = parseCommandClass(p.toFile());
                            if (info != null) {
                                // Store by simple class name for lookup
                                commandsByClassName.put(info.className, info);
                            }
                        } catch (Exception e) {
                            getLog().warn("Failed to parse command class: " + p + " - " + e.getMessage());
                        }
                    });
        }
    }

    private void parseCommandHierarchy(
            File mainFile, Map<String, CommandInfo> commandsByClassName,
            JBangCommandModel model)
            throws IOException {

        List<String> lines = Files.readAllLines(mainFile.toPath());

        // Find the start of the commandLine builder chain
        int startLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("commandLine = new CommandLine(main)")) {
                startLine = i;
                break;
            }
        }

        if (startLine == -1) {
            return;
        }

        // Parse line by line, using indentation to determine hierarchy
        // We use a stack to track parent commands at each indentation level
        Stack<ParentInfo> parentStack = new Stack<>();

        for (int i = startLine; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmedLine = line.trim();

            // Check if we've reached the end of the chain
            if (trimmedLine.startsWith(".setParameterExceptionHandler") || trimmedLine.equals(";")) {
                break;
            }

            // Look for .addSubcommand pattern
            Matcher matcher = SUBCOMMAND_PATTERN.matcher(trimmedLine);
            if (matcher.find()) {
                String cmdName = matcher.group(1);
                String className = matcher.group(2);

                CommandInfo info = commandsByClassName.get(className);
                if (info == null) {
                    getLog().debug("Command class not found: " + className);
                    continue;
                }

                // Calculate indentation level (number of leading spaces)
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') {
                        indent++;
                    } else if (c == '\t') {
                        indent += 4;
                    } else {
                        break;
                    }
                }

                // Pop parents that are at same or deeper indentation level
                // This handles transitioning from subcommands back to parent level
                while (!parentStack.isEmpty() && parentStack.peek().indent >= indent) {
                    parentStack.pop();
                }

                // Determine parent path
                String parentPath = "";
                if (!parentStack.isEmpty()) {
                    parentPath = parentStack.peek().command.getFullName();
                }

                JBangCommand cmd = createCommand(info, cmdName, parentPath);

                // Add to parent or model
                if (parentStack.isEmpty()) {
                    model.addCommand(cmd);
                } else {
                    parentStack.peek().command.addSubcommand(cmd);
                }

                // Check if this command has subcommands by looking at the line ending
                // Count closing parens at end of line
                String lineEnd = trimmedLine.replaceAll("\\s+", "");
                int closingParens = 0;
                for (int j = lineEnd.length() - 1; j >= 0 && lineEnd.charAt(j) == ')'; j--) {
                    closingParens++;
                }

                // Pattern: .addSubcommand("x", new CommandLine(new X(main))
                // - Ends with 2 parens ()) = has subcommands (CommandLine not closed)
                // - Ends with 3 parens ())) = leaf command (closes CommandLine + addSubcommand)
                if (closingParens == 2) {
                    // This command may have subcommands - push onto stack
                    parentStack.push(new ParentInfo(cmd, indent));
                }
                // If closingParens >= 3, it's a leaf command and doesn't have subcommands
            }
        }
    }

    private static class ParentInfo {
        final JBangCommand command;
        final int indent;

        ParentInfo(JBangCommand command, int indent) {
            this.command = command;
            this.indent = indent;
        }
    }

    private JBangCommand createCommand(CommandInfo info, String cmdName, String parentPath) {
        JBangCommand cmd = new JBangCommand();
        cmd.setName(cmdName);
        cmd.setFullName(parentPath.isEmpty() ? cmdName : parentPath + " " + cmdName);
        cmd.setDescription(info.description);
        cmd.setDeprecated(info.deprecated);
        cmd.setSourceClass(info.qualifiedName);

        // Add all options including inherited ones
        for (OptionInfo opt : info.options) {
            if (!opt.hidden) {
                JBangCommandOption option = new JBangCommandOption();
                option.setNames(opt.names);
                option.setDescription(opt.description);
                option.setDefaultValue(opt.defaultValue);
                option.setJavaType(opt.javaType);
                option.setType(MojoHelper.getType(opt.javaType, false, false));
                option.setRequired(opt.required);
                option.setDeprecated(opt.deprecated);
                option.setHidden(opt.hidden);
                option.setParamLabel(opt.paramLabel);
                cmd.addOption(option);
            }
        }

        // Sort options by name
        cmd.getOptions().sort((a, b) -> a.getNames().compareToIgnoreCase(b.getNames()));

        return cmd;
    }

    private CommandInfo parseCommandClass(File file) throws IOException {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(file);

        // Check if this class has @Command annotation
        AnnotationSource<?> commandAnn = findAnnotation(clazz, "Command");
        if (commandAnn == null) {
            return null;
        }

        CommandInfo info = new CommandInfo();
        info.className = clazz.getName();
        info.qualifiedName = clazz.getQualifiedName();

        // Parse @Command annotation
        String name = commandAnn.getStringValue("name");
        if (name != null) {
            info.name = name.replace("\"", "");
        } else {
            info.name = clazz.getName().toLowerCase();
        }

        String description = commandAnn.getStringValue("description");
        if (description != null) {
            description = description.replace("\"", "");
            // Clean up description - remove help hints
            if (description.contains("(use")) {
                description = description.substring(0, description.indexOf("(use")).trim();
            }
            info.description = description;
        }

        // Check for deprecated
        if (clazz.getAnnotation(Deprecated.class) != null) {
            info.deprecated = true;
        }

        // Parse options from this class AND parent classes
        info.options = new ArrayList<>();
        parseOptionsFromClassHierarchy(clazz, info.options, file.getParentFile());

        return info;
    }

    private void parseOptionsFromClassHierarchy(JavaClassSource clazz, List<OptionInfo> options, File baseDir) {
        // First, parse options from parent class (if any)
        String superType = clazz.getSuperType();
        if (superType != null && !superType.equals("java.lang.Object") && !superType.equals("Object")) {
            // Try to find and parse the parent class
            String parentClassName = superType;
            if (parentClassName.contains(".")) {
                parentClassName = parentClassName.substring(parentClassName.lastIndexOf('.') + 1);
            }

            // Search for parent class file
            File parentFile = findClassFile(baseDir, parentClassName);
            if (parentFile != null && parentFile.exists()) {
                try {
                    JavaClassSource parentClazz = (JavaClassSource) Roaster.parse(parentFile);
                    parseOptionsFromClassHierarchy(parentClazz, options, parentFile.getParentFile());
                } catch (Exception e) {
                    getLog().debug("Could not parse parent class: " + parentClassName + " - " + e.getMessage());
                }
            }
        }

        // Then parse options from this class (will override parent options with same name)
        for (FieldSource<JavaClassSource> field : clazz.getFields()) {
            OptionInfo option = parseOption(field);
            if (option != null) {
                // Check if we already have an option with the same name (from parent)
                // and replace it with the child's version
                options.removeIf(o -> o.names.equals(option.names));
                options.add(option);
            }
        }
    }

    private File findClassFile(File dir, String className) {
        // Search in current directory
        File file = new File(dir, className + ".java");
        if (file.exists()) {
            return file;
        }

        // Search in parent directories up to commands root
        File parent = dir.getParentFile();
        while (parent != null && parent.getPath().contains("commands")) {
            file = new File(parent, className + ".java");
            if (file.exists()) {
                return file;
            }

            // Also search subdirectories
            File[] subdirs = parent.listFiles(File::isDirectory);
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    file = new File(subdir, className + ".java");
                    if (file.exists()) {
                        return file;
                    }
                }
            }

            parent = parent.getParentFile();
        }

        // Also try common locations
        File commandsRoot = dir;
        while (commandsRoot.getParentFile() != null && !commandsRoot.getName().equals("commands")) {
            commandsRoot = commandsRoot.getParentFile();
        }

        // Search recursively from commands root
        return findClassFileRecursive(commandsRoot, className);
    }

    private File findClassFileRecursive(File dir, String className) {
        File file = new File(dir, className + ".java");
        if (file.exists()) {
            return file;
        }

        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                File found = findClassFileRecursive(subdir, className);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private AnnotationSource<?> findAnnotation(JavaClassSource clazz, String annotationName) {
        AnnotationSource<?> ann = clazz.getAnnotation("picocli.CommandLine." + annotationName);
        if (ann == null) {
            ann = clazz.getAnnotation("CommandLine." + annotationName);
        }
        if (ann == null) {
            ann = clazz.getAnnotation(annotationName);
        }
        return ann;
    }

    private OptionInfo parseOption(FieldSource<JavaClassSource> field) {
        AnnotationSource<?> optionAnn = field.getAnnotation("picocli.CommandLine.Option");
        if (optionAnn == null) {
            optionAnn = field.getAnnotation("CommandLine.Option");
        }
        if (optionAnn == null) {
            optionAnn = field.getAnnotation("Option");
        }
        if (optionAnn == null) {
            return null;
        }

        OptionInfo option = new OptionInfo();

        // Parse names
        String namesValue = optionAnn.getStringValue("names");
        if (namesValue != null) {
            namesValue = namesValue.replace("\"", "").replace("{", "").replace("}", "").trim();
            option.names = namesValue;
        } else {
            // Try to get array value
            String[] names = optionAnn.getStringArrayValue("names");
            if (names != null && names.length > 0) {
                option.names = String.join(", ", names);
            }
        }

        if (option.names == null || option.names.isEmpty()) {
            return null;
        }

        // Parse description
        String description = optionAnn.getStringValue("description");
        if (description != null) {
            description = description.replace("\"", "");
            // Remove Picocli placeholders that AsciiDoctor interprets as attributes
            description = sanitizeDescription(description);
            option.description = description;
        }

        // Parse default value
        String defaultValue = optionAnn.getStringValue("defaultValue");
        if (defaultValue != null) {
            defaultValue = defaultValue.replace("\"", "");
            defaultValue = sanitizeDescription(defaultValue);
            option.defaultValue = defaultValue;
        }

        // Parse required
        String required = optionAnn.getStringValue("required");
        if ("true".equals(required)) {
            option.required = true;
        }

        // Parse hidden
        String hidden = optionAnn.getStringValue("hidden");
        if ("true".equals(hidden)) {
            option.hidden = true;
        }

        // Parse param label
        String paramLabel = optionAnn.getStringValue("paramLabel");
        if (paramLabel != null) {
            option.paramLabel = paramLabel.replace("\"", "");
        }

        // Get Java type from field
        option.javaType = field.getType().getQualifiedName();

        // Check for deprecated
        if (field.getAnnotation(Deprecated.class) != null) {
            option.deprecated = true;
        }

        return option;
    }

    /**
     * Sanitizes description text by removing or escaping Picocli placeholders that AsciiDoctor would interpret as
     * attribute references.
     */
    private String sanitizeDescription(String description) {
        if (description == null) {
            return null;
        }
        // Remove ${COMPLETION-CANDIDATES} placeholder
        description = description.replace("${COMPLETION-CANDIDATES}", "");
        description = description.replace("(${COMPLETION-CANDIDATES})", "");
        // Remove ${camel-version} and similar version placeholders
        description = description.replaceAll("\\$\\{[^}]+\\}", "");
        // Clean up any resulting double spaces
        description = description.replaceAll("\\s+", " ").trim();
        return description;
    }

    // Helper classes
    private static class CommandInfo {
        String className;
        String qualifiedName;
        String name;
        String description;
        boolean deprecated;
        List<OptionInfo> options;
    }

    private static class OptionInfo {
        String names;
        String description;
        String defaultValue;
        String javaType;
        boolean required;
        boolean hidden;
        boolean deprecated;
        String paramLabel;
    }
}
