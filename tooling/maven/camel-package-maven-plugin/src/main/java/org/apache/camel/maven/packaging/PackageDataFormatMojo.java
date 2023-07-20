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
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.maven.packaging.generics.ClassUtil;
import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.DataFormatModel.DataFormatOptionModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.SupportLevel;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor information for easier auto-discovery in
 * Camel.
 */
@Mojo(name = "generate-dataformats-list", threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class PackageDataFormatMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for generated dataformats file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File dataFormatOutDir;

    /**
     * The output directory for generated dataformats file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File configurerSourceOutDir;

    /**
     * The output directory for generated dataformats file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File configurerResourceOutDir;

    /**
     * The output directory for generated dataformats file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File schemaOutDir;

    protected ClassLoader projectClassLoader;
    private final Map<String, Optional<JavaClassSource>> sources = new HashMap<>();

    public PackageDataFormatMojo() {
    }

    public PackageDataFormatMojo(Log log, MavenProject project, MavenProjectHelper projectHelper,
                                 File dataFormatOutDir, File configurerSourceOutDir,
                                 File configurerResourceOutDir, File schemaOutDir,
                                 BuildContext buildContext) {
        setLog(log);
        this.project = project;
        this.projectHelper = projectHelper;
        this.dataFormatOutDir = dataFormatOutDir;
        this.configurerSourceOutDir = configurerSourceOutDir;
        this.configurerResourceOutDir = configurerResourceOutDir;
        this.schemaOutDir = schemaOutDir;
        this.buildContext = buildContext;
    }

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the threads it
     *                                                        generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareDataFormat();
    }

    public int prepareDataFormat() throws MojoExecutionException {
        Log log = getLog();

        File camelMetaDir = new File(dataFormatOutDir, "META-INF/services/org/apache/camel/");

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know
        // about that directory
        if (projectHelper != null) {
            projectHelper.addResource(project, dataFormatOutDir.getPath(),
                    Collections.singletonList("**/dataformat.properties"), Collections.emptyList());
        }

        if (!haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/dataformat")) {
            return 0;
        }

        Map<String, String> javaTypes = new HashMap<>();

        StringBuilder buffer = new StringBuilder();
        int count = 0;

        File f = new File(project.getBasedir(), "target/classes");
        f = new File(f, "META-INF/services/org/apache/camel/dataformat");
        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File file : files) {
                    String javaType = readClassFromCamelResource(file, buffer, buildContext);
                    if (!file.isDirectory() && file.getName().charAt(0) != '.') {
                        count++;
                    }
                    if (javaType != null) {
                        javaTypes.put(file.getName(), javaType);
                    }
                }
            }
        }

        // is this from Apache Camel then the data format is out of the box and
        // we should enrich the json schema with more details
        boolean apacheCamel = "org.apache.camel".equals(project.getGroupId());

        // find camel-core and grab the data format model from there, and enrich
        // this model with information from this artifact
        // and create json schema model file for this data format
        try {
            if (apacheCamel && count > 0) {
                File core = PackageHelper.findCamelCoreModelDirectory(project.getBasedir());
                if (core != null) {
                    for (Map.Entry<String, String> entry : javaTypes.entrySet()) {
                        String name = entry.getKey();
                        String javaType = entry.getValue();
                        String modelName = asModelName(name);

                        String json = PackageHelper.loadText(new File(
                                core,
                                "target/classes/org/apache/camel/model/dataformat/" + modelName + PackageHelper.JSON_SUFIX));

                        // any excluded properties
                        Class<?> clazz = loadClass(javaType);
                        Metadata metadata = clazz.getAnnotation(Metadata.class);
                        String included = "";
                        String excluded = "";
                        if (metadata != null) {
                            included = metadata.includeProperties();
                            excluded = metadata.excludeProperties();
                        }

                        final DataFormatModel dataFormatModel
                                = extractDataFormatModel(project, json, name, clazz, included, excluded);
                        if (!modelName.equals(name)) {
                            /* Prefer description from the clazz */
                            setDescriptionFromClass(clazz, dataFormatModel);
                        }
                        if (log.isDebugEnabled()) {
                            log.debug("Model: " + dataFormatModel);
                        }
                        String schema = JsonMapper.createParameterJsonSchema(dataFormatModel);
                        if (log.isDebugEnabled()) {
                            log.debug("JSON schema:\n" + schema);
                        }

                        // write this to the directory
                        Path out = schemaOutDir.toPath().resolve(schemaSubDirectory(dataFormatModel.getJavaType()))
                                .resolve(name + PackageHelper.JSON_SUFIX);
                        updateResource(schemaOutDir.toPath(),
                                schemaSubDirectory(dataFormatModel.getJavaType()) + "/" + name + PackageHelper.JSON_SUFIX,
                                schema);

                        if (log.isDebugEnabled()) {
                            log.debug("Generated " + out + " containing JSON schema for " + name + " data format");
                        }

                        String cn = javaType.substring(javaType.lastIndexOf('.') + 1);
                        String pn = javaType.substring(0, javaType.length() - cn.length() - 1);
                        Set<String> names = dataFormatModel.getOptions().stream().map(DataFormatOptionModel::getName)
                                .collect(Collectors.toSet());
                        List<DataFormatOptionModel> options = parseConfigurationSource(project, javaType);
                        options.removeIf(o -> !names.contains(o.getName()));
                        options.stream().map(DataFormatOptionModel::getName).collect(Collectors.toList())
                                .forEach(names::remove);
                        names.removeAll(List.of("id"));
                        if (!names.isEmpty()) {
                            log.warn("Unmapped options: " + String.join(",", names));
                        }
                        updateResource(configurerSourceOutDir.toPath(),
                                pn.replace('.', '/') + "/" + cn + "Configurer.java",
                                generatePropertyConfigurer(pn, cn + "Configurer", cn, options));
                        updateResource(configurerResourceOutDir.toPath(),
                                "META-INF/services/org/apache/camel/configurer/" + name + "-dataformat",
                                generateMetaInfConfigurer(pn + "." + cn + "Configurer"));
                    }
                } else {
                    throw new MojoExecutionException(
                            "Error finding core/camel-core/target/camel-core-model-" + project.getVersion()
                                                     + ".jar file. Make sure camel-core has been built first.");
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error loading dataformat model from camel-core. Reason: " + e, e);
        }

        if (count > 0) {
            String names = Stream.of(buffer.toString().split(" ")).sorted().collect(Collectors.joining(" "));
            String properties = createProperties(project, "dataFormats", names);
            updateResource(camelMetaDir.toPath(), "dataformat.properties", properties);
            log.info("Generated dataformat.properties containing " + count + " Camel "
                     + (count > 1 ? "dataformats: " : "dataformat: ") + names);
        } else {
            log.debug(
                    "No META-INF/services/org/apache/camel/dataformat directory found. Are you sure you have created a Camel data format?");
        }

        return count;
    }

    private void setDescriptionFromClass(Class<?> clazz, final DataFormatModel dataFormatModel) {
        javaClassSource(clazz.getName()).ifPresent(src -> {
            String doc = src.getJavaDoc().getFullText();
            if (doc != null) {
                // need to sanitize the description first (we only want a
                // summary)
                doc = JavadocHelper.sanitizeDescription(doc, true);
                // the javadoc may actually be empty, so only change the doc
                // if we got something
                if (!Strings.isNullOrEmpty(doc)) {
                    dataFormatModel.setDescription(doc);
                }
            }
        });
    }

    private static DataFormatModel extractDataFormatModel(
            MavenProject project, String json, String name, Class<?> javaType,
            String includedProperties, String excludedProperties) {
        EipModel def = JsonMapper.generateEipModel(json);
        DataFormatModel model = new DataFormatModel();
        model.setName(name);
        model.setTitle(asModelTitle(name, def.getTitle()));
        model.setDescription(def.getDescription());
        model.setFirstVersion(asModelFirstVersion(name, def.getFirstVersion()));
        model.setLabel(def.getLabel());
        model.setDeprecated(def.isDeprecated());
        model.setDeprecationNote(def.getDeprecationNote());
        model.setDeprecatedSince(project.getProperties().getProperty("deprecatedSince"));
        model.setJavaType(javaType.getCanonicalName());
        model.setModelName(def.getName());
        model.setModelJavaType(def.getJavaType());
        model.setGroupId(project.getGroupId());
        model.setArtifactId(project.getArtifactId());
        model.setVersion(project.getVersion());

        // grab level from annotation, pom.xml or default to stable
        String level = project.getProperties().getProperty("supportLevel");
        boolean experimental = ClassUtil.hasAnnotation("org.apache.camel.Experimental", javaType);
        if (experimental) {
            model.setSupportLevel(SupportLevel.Experimental);
        } else if (level != null) {
            model.setSupportLevel(SupportLevel.safeValueOf(level));
        } else {
            model.setSupportLevel(SupportLevelHelper.defaultSupportLevel(model.getFirstVersion(), model.getVersion()));
        }

        for (EipOptionModel opt : def.getOptions()) {
            DataFormatOptionModel option = new DataFormatOptionModel();

            if (excludedProperties.contains(opt.getName())) {
                // skip excluded
                continue;
            }
            if (!includedProperties.isEmpty() && !includedProperties.contains(opt.getName())) {
                // skip if not included
                continue;
            }

            option.setName(opt.getName());
            option.setKind(opt.getKind());
            option.setDisplayName(opt.getDisplayName());
            option.setGroup(opt.getGroup());
            option.setLabel(opt.getLabel());
            option.setRequired(opt.isRequired());
            option.setType(opt.getType());
            option.setJavaType(opt.getJavaType());
            option.setEnums(opt.getEnums());
            option.setOneOfs(opt.getOneOfs());
            option.setPrefix(opt.getPrefix());
            option.setOptionalPrefix(opt.getOptionalPrefix());
            option.setMultiValue(opt.isMultiValue());
            option.setDeprecated(opt.isDeprecated());
            option.setDeprecationNote(opt.getDeprecationNote());
            option.setSecret(opt.isSecret());
            option.setDefaultValue(opt.getDefaultValue());
            option.setDefaultValueNote(opt.getDefaultValueNote());
            option.setAsPredicate(opt.isAsPredicate());
            option.setConfigurationClass(opt.getConfigurationClass());
            option.setConfigurationField(opt.getConfigurationField());
            option.setDescription(opt.getDescription());

            if ("type".equals(option.getName()) && "bindy".equals(model.getModelName())) {
                switch (name) {
                    case "bindyCsv":
                        option.setDefaultValue("Csv");
                        break;
                    case "bindyFixed":
                        option.setDefaultValue("Fixed");
                        break;
                    case "bindyKvp":
                        option.setDefaultValue("KeyValue");
                        break;
                    default:
                }

            }
            if ("objectMapper".equals(option.getName()) && "johnzon".equals(name)) {
                option.setDisplayName("Mapper");
                option.setDescription("Lookup and use the existing Mapper with the given id.");
            }
            if ("objectMapper".equals(option.getName()) && "jsonb".equals(name)) {
                option.setDisplayName("Jsonb instance");
                option.setDescription("Lookup and use the existing Jsonb instance with the given id.");
            }
            if ("library".equals(option.getName()) && "json".equals(model.getModelName())) {
                switch (name) {
                    case "gson":
                        option.setDefaultValue("Gson");
                        break;
                    case "jackson":
                        option.setDefaultValue("Jackson");
                        break;
                    case "johnzon":
                        option.setDefaultValue("Johnzon");
                        break;
                    case "jsonb":
                        option.setDefaultValue("JSON-B");
                        break;
                    case "json-fastson":
                        option.setDefaultValue("Fastjson");
                        break;
                    default:
                }
            }
            model.addOption(option);
        }
        return model;
    }

    private static String readClassFromCamelResource(File file, StringBuilder buffer, BuildContext buildContext)
            throws MojoExecutionException {
        // skip directories as there may be a sub .resolver directory
        if (file.isDirectory()) {
            return null;
        }
        String name = file.getName();
        if (name.charAt(0) != '.') {
            if (buffer.length() > 0) {
                buffer.append(" ");
            }
            buffer.append(name);
        }

        if (!buildContext.hasDelta(file)) {
            // if this file has not changed,
            // then no need to store the javatype
            // for the json file to be generated again
            // (but we do need the name above!)
            return null;
        }

        // find out the javaType for each data format
        try {
            String text = PackageHelper.loadText(file);
            Map<String, String> map = PackageHelper.parseAsMap(text);
            return map.get("class");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read file " + file + ". Reason: " + e, e);
        }
    }

    private static String asModelName(String name) {
        // special for some data formats
        if ("gson".equals(name) || "jackson".equals(name) || "johnzon".equals(name)
                || "fastjson".equals(name) || "jsonb".equals(name)) {
            return "json";
        } else if ("bindyCsv".equals(name) || "bindyFixed".equals(name) || "bindyKvp".equals(name)) {
            return "bindy";
        } else if ("snakeYaml".equals(name)) {
            return "yaml";
        } else if ("avroJackson".equals(name)) {
            return "avro";
        } else if ("protobufJackson".equals(name)) {
            return "protobuf";
        }
        return name;
    }

    private static String asModelFirstVersion(String name, String firstVersion) {
        switch (name) {
            case "gson":
                return "2.10.0";
            case "jackson":
                return "2.0.0";
            case "johnzon":
                return "2.18.0";
            case "jsonb":
                return "3.7.0";
            case "fastjson":
                return "2.20.0";
            case "avroJackson":
                return "3.10.0";
            case "protobufJackson":
                return "3.10.0";
            default:
                return firstVersion;
        }
    }

    private static String asModelTitle(String name, String title) {
        // special for some data formats
        if ("gson".equals(name)) {
            return "JSON Gson";
        } else if ("jackson".equals(name)) {
            return "JSON Jackson";
        } else if ("avroJackson".equals(name)) {
            return "Avro Jackson";
        } else if ("protobufJackson".equals(name)) {
            return "Protobuf Jackson";
        } else if ("johnzon".equals(name)) {
            return "JSON Johnzon";
        } else if ("jsonb".equals(name)) {
            return "JSON JSON-B";
        } else if ("fastjson".equals(name)) {
            return "JSON Fastjson";
        } else if ("bindyCsv".equals(name)) {
            return "Bindy CSV";
        } else if ("bindyFixed".equals(name)) {
            return "Bindy Fixed Length";
        } else if ("bindyKvp".equals(name)) {
            return "Bindy Key Value Pair";
        } else if ("snakeYaml".equals(name)) {
            return "YAML SnakeYAML";
        }
        return title;
    }

    private static String schemaSubDirectory(String javaType) {
        int idx = javaType.lastIndexOf('.');
        String pckName = javaType.substring(0, idx);
        return pckName.replace('.', '/');
    }

    private List<DataFormatOptionModel> parseConfigurationSource(MavenProject project, String className)
            throws IOException {
        final List<DataFormatOptionModel> answer = new ArrayList<>();

        Optional<JavaClassSource> optClazz = javaClassSource(className);
        if (!optClazz.isPresent()) {
            return Collections.emptyList();
        }
        JavaClassSource clazz = optClazz.get();
        List<FieldSource<JavaClassSource>> fields = clazz.getFields();
        // filter out final or static fields
        fields = fields.stream().filter(f -> !f.isFinal() && !f.isStatic()).collect(Collectors.toList());
        fields.forEach(f -> {
            String name = f.getName();
            String javaType = f.getType().getQualifiedName();
            if (f.getType().isArray()) {
                javaType += "[]";
            }
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            MethodSource<?> setter = clazz.getMethod(setterName, javaType);
            if (setter != null) {
                DataFormatOptionModel model = new DataFormatOptionModel();
                model.setName(name);
                model.setJavaType(javaType);
                answer.add(model);
            }
        });

        if (clazz.getSuperType() != null) {
            answer.addAll(parseConfigurationSource(project, clazz.getSuperType()));
        }

        return answer;
    }

    private Optional<JavaClassSource> javaClassSource(String className) {
        return sources.computeIfAbsent(className, this::doParseJavaClassSource);
    }

    private Optional<JavaClassSource> doParseJavaClassSource(String className) {
        try {
            Path srcDir = project.getBasedir().toPath().resolve("src/main/java");
            // Remove <.*> from className, as the string may contain generic types
            Path file = srcDir.resolve(className.replaceAll("<.*>", "").replace('.', '/') + ".java");
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            return Optional.of((JavaClassSource) Roaster.parse(file.toFile()));
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse java class " + className, e);
        }
    }

    public static String generatePropertyConfigurer(String pn, String cn, String en, Collection<DataFormatOptionModel> options)
            throws IOException {

        try (StringWriter w = new StringWriter()) {
            w.write("/* " + GENERATED_MSG + " */\n");
            w.write("package " + pn + ";\n");
            w.write("\n");
            w.write("import java.util.HashMap;\n");
            w.write("import java.util.Map;\n");
            w.write("\n");
            w.write("import org.apache.camel.CamelContext;\n");
            w.write("import org.apache.camel.spi.GeneratedPropertyConfigurer;\n");
            w.write("import org.apache.camel.support.component.PropertyConfigurerSupport;\n");
            w.write("\n");
            w.write("/**\n");
            w.write(" * " + GENERATED_MSG + "\n");
            w.write(" */\n");
            w.write("@SuppressWarnings(\"unchecked\")\n");
            w.write("public class " + cn + " extends PropertyConfigurerSupport implements GeneratedPropertyConfigurer {\n");
            w.write("\n");
            w.write("    @Override\n");
            w.write("    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {\n");
            w.write("        " + en + " dataformat = (" + en + ") target;\n");
            w.write("        switch (ignoreCase ? name.toLowerCase() : name) {\n");
            for (DataFormatOptionModel option : options) {
                String name = option.getName();
                if ("id".equals(name)) {
                    continue;
                }
                String setter = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                String type = Strings.canonicalClassName(option.getJavaType());
                if (!name.toLowerCase().equals(name)) {
                    w.write(String.format("        case \"%s\":\n", name.toLowerCase()));
                }
                w.write(String.format(
                        "        case \"%s\": dataformat.%s(property(camelContext, %s.class, value)); return true;\n", name,
                        setter, type));
            }
            w.write("        default: return false;\n");
            w.write("        }\n");
            w.write("    }\n");
            w.write("\n");
            w.write("}\n");
            w.write("\n");
            return w.toString();
        }
    }

    public static String generateMetaInfConfigurer(String fqn) {
        return "# " + GENERATED_MSG + NL + "class=" + fqn + NL;
    }

}
