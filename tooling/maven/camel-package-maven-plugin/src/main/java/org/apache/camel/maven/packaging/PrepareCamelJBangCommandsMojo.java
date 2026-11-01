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
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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

/**
 * Prepares camel-jbang by generating the metadata of its commands, for the documentation and for camel ask.
 * <p>
 * The commands are read from the command model of the CLI, with the classes of the project: {@link #METADATA_CLASS} of
 * camel-jbang-core turns the model into plain data. In camel-jbang-core that is the commands of the CLI, and in a
 * camel-jbang plugin module the commands that its plugin adds. A plugin writes its metadata to
 * {@link #PLUGIN_METADATA_FILE}, so it does not shadow the metadata of camel-jbang-core on the classpath.
 */
@Mojo(name = "prepare-jbang-commands", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class PrepareCamelJBangCommandsMojo extends AbstractGeneratorMojo {

    public static final String CORE_METADATA_FILE = "META-INF/camel-jbang-commands-metadata.json";
    public static final String PLUGIN_METADATA_FILE = "META-INF/camel-jbang-plugin-commands-metadata.json";

    /**
     * The class of camel-jbang-core that gives the commands of the CLI as plain data.
     */
    static final String METADATA_CLASS = "org.apache.camel.dsl.jbang.core.commands.CommandMetadata";

    private static final String PLUGIN_FACTORY_DIR = "META-INF/services/org/apache/camel/camel-jbang-plugin";
    private static final String PLUGIN_PREFIX = "camel-jbang-plugin-";

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
        try {
            JBangCommandModel model = new JBangCommandModel();
            String metadataFile;
            if (new File(commandsDir, "CamelJBangMain.java").exists()) {
                metadataFile = CORE_METADATA_FILE;
                addCommands(model, readCommands("commands", null), null);
            } else {
                File pluginFile = findPluginFile();
                if (pluginFile == null) {
                    getLog().debug("No camel-jbang plugin found in: " + outFolder);
                    return;
                }
                metadataFile = PLUGIN_METADATA_FILE;
                String plugin = pluginFile.getName().substring(PLUGIN_PREFIX.length());
                addCommands(model, readCommands("pluginCommands", pluginClass(pluginFile)), plugin);
            }

            if (!model.getCommands().isEmpty()) {
                sortCommandsRecursively(model.getCommands());

                String json = JsonMapper.createJsonSchema(model);
                updateResource(outFolder.toPath(), metadataFile, json);
                getLog().info("Generated JBang commands metadata with " + countCommands(model.getCommands()) + " commands");
            }
        } catch (Exception e) {
            throw new MojoFailureException("Error generating JBang commands metadata", e);
        }
    }

    /**
     * The file that registers the plugin of the module: META-INF/services/.../camel-jbang-plugin-xxx with the class of
     * the plugin, or null for a module without a plugin.
     */
    private File findPluginFile() {
        File[] files = new File(outFolder, PLUGIN_FACTORY_DIR).listFiles(f -> f.getName().startsWith(PLUGIN_PREFIX));
        return files != null && files.length == 1 ? files[0] : null;
    }

    private static String pluginClass(File pluginFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(pluginFile.toPath())) {
            properties.load(is);
        }
        return properties.getProperty("class");
    }

    /**
     * Reads the commands with {@link #METADATA_CLASS} from the classes of the project.
     *
     * @param method      commands, or pluginCommands for the commands of a plugin
     * @param pluginClass the class of the plugin, or null
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readCommands(String method, String pluginClass) throws Exception {
        ClassLoader loader = getProjectClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader old = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            Class<?> metadata = loader.loadClass(METADATA_CLASS);
            if (pluginClass == null) {
                return (List<Map<String, Object>>) metadata.getMethod(method).invoke(null);
            }
            return (List<Map<String, Object>>) metadata.getMethod(method, Class.class)
                    .invoke(null, loader.loadClass(pluginClass));
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } finally {
            thread.setContextClassLoader(old);
        }
    }

    /**
     * Adds the commands that are not hidden to the model, and their sub-commands to theirs.
     *
     * @param commands the commands as read with {@link #METADATA_CLASS}
     * @param plugin   the name of the plugin of the commands, or null
     */
    static void addCommands(JBangCommandModel model, List<Map<String, Object>> commands, String plugin) {
        for (Map<String, Object> command : commands) {
            if (!(Boolean) command.get("hidden")) {
                model.addCommand(createCommand(command, "", plugin));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static JBangCommand createCommand(Map<String, Object> command, String parentPath, String plugin) {
        JBangCommand cmd = new JBangCommand();
        String name = (String) command.get("name");
        cmd.setName(name);
        cmd.setFullName(parentPath.isEmpty() ? name : parentPath + " " + name);
        String description = (String) command.get("description");
        // without the hint to the help
        if (description.contains("(use")) {
            description = description.substring(0, description.indexOf("(use")).trim();
        }
        cmd.setDescription(description.isEmpty() ? null : description);
        cmd.setDeprecated((Boolean) command.get("deprecated"));
        cmd.setSourceClass((String) command.get("sourceClass"));
        cmd.setPlugin(plugin);

        for (Map<String, Object> opt : (List<Map<String, Object>>) command.get("options")) {
            if (!(Boolean) opt.get("hidden")) {
                cmd.addOption(createOption(opt));
            }
        }
        cmd.getOptions().sort((a, b) -> a.getNames().compareToIgnoreCase(b.getNames()));

        for (Map<String, Object> sub : (List<Map<String, Object>>) command.get("subcommands")) {
            if (!(Boolean) sub.get("hidden")) {
                cmd.addSubcommand(createCommand(sub, cmd.getFullName(), plugin));
            }
        }
        return cmd;
    }

    @SuppressWarnings("unchecked")
    private static JBangCommandOption createOption(Map<String, Object> opt) {
        JBangCommandOption option = new JBangCommandOption();
        option.setNames((String) opt.get("names"));
        String description = (String) opt.get("description");
        List<String> candidates = (List<String>) opt.get("completionCandidates");
        if (candidates != null) {
            description = description.replace("${COMPLETION-CANDIDATES}", String.join(", ", candidates));
        }
        description = sanitizeDescription(description);
        option.setDescription(description.isEmpty() ? null : description);
        option.setDefaultValue(sanitizeDescription((String) opt.get("defaultValue")));
        String javaType = (String) opt.get("javaType");
        option.setJavaType(javaType);
        option.setType(MojoHelper.getType(javaType, false, false));
        option.setRequired((Boolean) opt.get("required"));
        option.setDeprecated((Boolean) opt.get("deprecated"));
        option.setParamLabel((String) opt.get("paramLabel"));
        return option;
    }

    /**
     * Sanitizes description text by removing the placeholders of the command line parser, which AsciiDoctor would
     * interpret as attribute references.
     */
    static String sanitizeDescription(String description) {
        if (description == null) {
            return null;
        }
        // Remove ${COMPLETION-CANDIDATES}, ${camel-version} and similar placeholders
        description = description.replaceAll("\\$\\{[^}]+\\}", "");
        // Clean up empty or near-empty parentheses left after placeholder removal
        // e.g. "(supports: )" or "()" or "( )"
        description = description.replaceAll("\\([^)]*:\\s*\\)", "");
        description = description.replaceAll("\\(\\s*\\)", "");
        // Clean up any resulting double spaces
        description = description.replaceAll("\\s+", " ").trim();
        return description;
    }

    private static void sortCommandsRecursively(List<JBangCommand> commands) {
        commands.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (JBangCommand cmd : commands) {
            if (cmd.hasSubcommands()) {
                sortCommandsRecursively(cmd.getSubcommands());
            }
        }
    }

    private static int countCommands(List<JBangCommand> commands) {
        int count = commands.size();
        for (JBangCommand cmd : commands) {
            count += countCommands(cmd.getSubcommands());
        }
        return count;
    }
}
