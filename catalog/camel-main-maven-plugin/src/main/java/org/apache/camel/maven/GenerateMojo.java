/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.maven.model.AutowireData;
import org.apache.camel.maven.model.SpringBootData;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static org.apache.camel.util.StringHelper.camelCaseToDash;

/**
 * Pre scans your project and prepares autowiring and spring-boot tooling support by classpath scanning.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateMojo extends AbstractMainMojo {

    /**
     * Whether generating autowiring is enabled.
     */
    @Parameter(property = "camel.autowireEnabled", defaultValue = "true")
    protected boolean autowireEnabled;

    /**
     * Whether generating spring boot tooling support is enabled.
     */
    @Parameter(property = "camel.springBootEnabled", defaultValue = "true")
    protected boolean springBootEnabled;

    /**
     * When autowiring has detected multiple implementations (2 or more) of a given interface, which
     * cannot be mapped, should they be logged so you can see and add manual mapping if needed.
     */
    @Parameter(property = "camel.logUnmapped", defaultValue = "false")
    protected boolean logUnmapped;

    /**
     * The output directory for generated autowire file
     */
    @Parameter(readonly = true, defaultValue = "${project.build.directory}/classes/META-INF/services/org/apache/camel/")
    protected File outAutowireFolder;

    /**
     * The output directory for generated spring boot tooling file
     */
    @Parameter(readonly = true, defaultValue = "${project.build.directory}/../src/main/resources/META-INF/")
    protected File outSpringBootFolder;

    /**
     * To exclude autowiring specific properties with these key names.
     * You can also configure a single entry and separate the excludes with comma
     */
    @Parameter(property = "camel.exclude")
    protected String[] exclude;

    /**
     * To include autowiring specific properties or component with these key names.
     * You can also configure a single entry and separate the includes with comma
     */
    @Parameter(property = "camel.include")
    protected String[] include;

    /**
     * To setup special mappings between known types as key=value pairs.
     * You can also configure a single entry and separate the mappings with comma
     */
    @Parameter(property = "camel.mappings")
    protected String[] mappings;

    /**
     * Optional mappings file loaded from classpath, with mapping that override any default mappings
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/camel-main-mappings.properties")
    protected File mappingsFile;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // load default mappings
        Properties mappingProperties = loadDefaultMappings();
        getLog().debug("Loaded default-mappings: " + mappingProperties);
        // add extra mappings
        if (this.mappings != null) {
            for (String m : this.mappings) {
                String key = StringHelper.before(m, "=");
                String value = StringHelper.after(m, "=");
                if (key != null && value != null) {
                    mappingProperties.setProperty(key, value);
                    getLog().debug("Added mapping from pom.xml: " + key + "=" + value);
                }
            }
        }
        Properties mappingFileProperties = loadMappingsFile();
        if (!mappingFileProperties.isEmpty()) {
            getLog().debug("Loaded mappings file: " + mappingsFile + " with mappings: " + mappingFileProperties);
            mappingProperties.putAll(mappingFileProperties);
        }

        final List<AutowireData> autowireData = new ArrayList<>();
        final List<SpringBootData> springBootData = new ArrayList<>();

        ComponentCallback callback = (componentName, componentJavaType, name, type, javaType, description, defaultValue) -> {
            // gather spring boot data
            // we want to use dash in the name
            String dash = camelCaseToDash(name);
            String key = "camel.component." + componentName + "." + dash;
            if (springBootEnabled) {
                getLog().debug("Spring Boot option: " + key);
                String sourceType = componentJavaType;
                springBootData.add(new SpringBootData(key, springBootJavaType(javaType), description, sourceType, defaultValue));
            }

            // check if we can do automatic autowire to complex singleton objects from classes in the classpath
            if (autowireEnabled && "object".equals(type)) {
                if (!isValidAutowirePropertyName(componentName, name)) {
                    getLog().debug("Skipping property name: " + name);
                    return;
                }
                try {
                    Class clazz = classLoader.loadClass(javaType);
                    if (clazz.isInterface() && isComplexUserType(clazz)) {
                        Set<Class<?>> classes = reflections.getSubTypesOf(clazz);
                        // filter classes (must not be interfaces, must be public, must not be abstract, must be top level) and also a valid autowire class
                        classes = classes.stream().filter(
                                c -> !c.isInterface()
                                        && Modifier.isPublic(c.getModifiers())
                                        && !Modifier.isAbstract(c.getModifiers())
                                        && c.getEnclosingClass() == null
                                        && isValidAutowireClass(c))
                                .collect(Collectors.toSet());
                        Class best = chooseBestKnownType(componentName, name, clazz, classes, mappingProperties);
                        if (best != null) {
                            key = "camel.component." + componentName + "." + dash;
                            String value = "#class:" + best.getName();
                            getLog().debug("Autowire: " + key + "=" + value);
                            autowireData.add(new AutowireData(key, value));

                            if (springBootEnabled) {
                                // gather additional spring boot data for this class
                                // we dont have documentation or default values
                                List<Method> setters = new ArrayList<>();
                                extraSetterMethods(best, setters);
                                // sort the setters
                                setters.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
                                for (Method m : setters) {
                                    String shortHand = IntrospectionSupport.getSetterShorthandName(m);
                                    String bootName = camelCaseToDash(shortHand);
                                    String bootKey = "camel.component." + componentName + "." + dash + "." + bootName;
                                    String bootJavaType = m.getParameterTypes()[0].getName();
                                    String sourceType = best.getName();
                                    getLog().debug("Spring Boot option: " + bootKey);
                                    String desc = "Auto discovered option from class: " + best.getName() + " to set the option via setter: " + m.getName();
                                    springBootData.add(new SpringBootData(bootKey, springBootJavaType(bootJavaType), desc, sourceType, null));
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    // ignore
                    getLog().debug("Cannot load class: " + name, e);
                }
            }
        };

        // execute with the callback
        doExecute(callback);

        // write the output files
        writeAutowireFile(autowireData);
        writeSpringBootFile(springBootData);
    }

    protected void writeSpringBootFile(List<SpringBootData> springBootData) throws MojoFailureException {
        if (!springBootData.isEmpty()) {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < springBootData.size(); i++) {
                SpringBootData row = springBootData.get(i);
                sb.append("    {\n");
                sb.append("      \"name\": \"" + row.getName() + "\",\n");
                sb.append("      \"type\": \"" + row.getJavaType() + "\",\n");
                sb.append("      \"sourceType\": \"" + row.getSourceType() + "\",\n");
                sb.append("      \"description\": \"" + row.getDescription() + "\"");
                if (row.getDefaultValue() != null) {
                    sb.append(",\n");
                    if (springBootDefaultValueQuotes(row.getJavaType())) {
                        sb.append("      \"defaultValue\": \"" + row.getDefaultValue() + "\"\n");
                    } else {
                        sb.append("      \"defaultValue\": " + row.getDefaultValue() + "\n");
                    }
                } else {
                    sb.append("\n");
                }
                if (i < springBootData.size() - 1) {
                    sb.append("    },\n");
                } else {
                    sb.append("    }\n");
                }
            }
            sb.append("  ]\n");
            sb.append("}\n");

            // okay then add the components into the main json at the end so they get merged together
            // load camel-main metadata
            String mainJson = loadCamelMainConfigurationMetadata();
            if (mainJson == null) {
                getLog().warn("Cannot load camel-main-configuration-metadata.json from within the camel-main JAR from the classpath."
                        + " Not possible to build spring boot configuration file for this project");
                return;
            }
            int pos = mainJson.lastIndexOf("    }");
            String newJson = mainJson.substring(0, pos);
            newJson = newJson + "    },\n";
            newJson = newJson + sb.toString();

            outSpringBootFolder.mkdirs();
            File file = new File(outSpringBootFolder, "spring-configuration-metadata.json");
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write(newJson.getBytes());
                IOHelper.close(fos);
                getLog().info("Created file: " + file);
            } catch (Throwable e) {
                throw new MojoFailureException("Cannot write to file " + file + " due " + e.getMessage(), e);
            }
        }
    }

    protected void writeAutowireFile(List<AutowireData> autowireData) throws MojoFailureException {
        if (!autowireData.isEmpty()) {
            outAutowireFolder.mkdirs();
            File file = new File(outAutowireFolder, "autowire.properties");
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write("# Generated by camel build tools\n".getBytes());
                for (AutowireData data : autowireData) {
                    fos.write(data.getKey().getBytes());
                    fos.write('=');
                    fos.write(data.getValue().getBytes());
                    fos.write('\n');
                }
                IOHelper.close(fos);
                getLog().info("Created file: " + file + " (autowire by classpath: " + autowireData.size() + ")");
            } catch (Throwable e) {
                throw new MojoFailureException("Cannot write to file " + file + " due " + e.getMessage(), e);
            }
        }
    }

    protected Properties loadDefaultMappings() throws MojoFailureException {
        Properties mappings = new OrderedProperties();
        try {
            InputStream is = GenerateMojo.class.getResourceAsStream("/default-mappings.properties");
            if (is != null) {
                mappings.load(is);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Cannot load default-mappings.properties from classpath");
        }
        return mappings;
    }

    protected Properties loadMappingsFile() throws MojoFailureException {
        Properties mappings = new OrderedProperties();
        if (mappingsFile.exists() && mappingsFile.isFile()) {
            try {
                InputStream is = new FileInputStream(mappingsFile);
                mappings.load(is);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot load file: " + mappingsFile);
            }
        }
        return mappings;
    }

    protected Class chooseBestKnownType(String componentName, String optionName, Class type, Set<Class<?>> candidates, Properties knownTypes) {
        String known = knownTypes.getProperty(type.getName());
        if (known != null) {
            for (String k : known.split(";")) {
                // special as we should skip this option
                if ("#skip#".equals(k)) {
                    return null;
                }
                // jump to after the leading prefix to have the classname
                if (k.startsWith("#class:")) {
                    k = k.substring(7);
                } else if (k.startsWith("#type:")) {
                    k = k.substring(6);
                }
                final String candiateName = k;
                Class found = candidates.stream().filter(c -> c.getName().equals(candiateName)).findFirst().orElse(null);
                if (found != null) {
                    return found;
                }
            }
        }

        if (candidates.size() == 1) {
            return candidates.iterator().next();
        } else if (candidates.size() > 1) {
            if (logUnmapped) {
                getLog().debug("Cannot chose best type: " + type.getName() + " among " + candidates.size() + " implementations: " + candidates);
                getLog().info("Cannot autowire option camel.component." + componentName + "." + optionName
                        + " as the interface: " + type.getName() + " has " + candidates.size() + " implementations in the classpath:");
                for (Class c : candidates) {
                    getLog().info("\t\t" + c.getName());
                }
            }
        }
        return null;
    }

    protected boolean isValidAutowirePropertyName(String componentName, String name) {
        // we want to regard names as the same if they are using dash or not, and also to be case insensitive.
        String prefix = "camel.component." + componentName + ".";
        name = StringHelper.dashToCamelCase(name);

        if (exclude != null && exclude.length > 0) {
            // works on components too
            for (String pattern : exclude) {
                pattern = pattern.trim();
                pattern = StringHelper.dashToCamelCase(pattern);
                if (PatternHelper.matchPattern(componentName, pattern)) {
                    return false;
                }
                if (PatternHelper.matchPattern(name, pattern) || PatternHelper.matchPattern(prefix + name, pattern)) {
                    return false;
                }
            }
        }

        if (include != null && include.length > 0) {
            for (String pattern : include) {
                pattern = pattern.trim();
                pattern = StringHelper.dashToCamelCase(pattern);
                if (PatternHelper.matchPattern(componentName, pattern)) {
                    return true;
                }
                if (PatternHelper.matchPattern(name, pattern) || PatternHelper.matchPattern(prefix + name, pattern)) {
                    return true;
                }
            }
            // we have include enabled and none matched so it should be false
            return false;
        }

        return true;
    }

    private static boolean isComplexUserType(Class type) {
        // lets consider all non java, as complex types
        return type != null && !type.isPrimitive() && !type.getName().startsWith("java.");
    }

    private static boolean isValidAutowireClass(Class clazz) {
        // skip all from Apache Camel and regular JDK as they would be default anyway
        return !clazz.getName().startsWith("org.apache.camel");
    }

    private String loadCamelMainConfigurationMetadata() throws MojoFailureException {
        try {
            InputStream is = classLoader.getResourceAsStream("META-INF/camel-main-configuration-metadata.json");
            String text = IOHelper.loadText(is);
            IOHelper.close(is);
            return text;
        } catch (Throwable e) {
            throw new MojoFailureException("Error during discovering camel-main from classpath due " + e.getMessage(), e);
        }
    }

    private static String springBootJavaType(String javaType) {
        if ("boolean".equalsIgnoreCase(javaType)) {
            return "java.lang.Boolean";
        } else if ("int".equalsIgnoreCase(javaType)) {
            return "java.lang.Integer";
        } else if ("long".equalsIgnoreCase(javaType)) {
            return "java.lang.Long";
        } else if ("string".equalsIgnoreCase(javaType)) {
            return "java.lang.String";
        }
        return javaType;
    }

    private static boolean springBootDefaultValueQuotes(String javaType) {
        if ("java.lang.Boolean".equalsIgnoreCase(javaType)) {
            return false;
        } else if ("java.lang.Integer".equalsIgnoreCase(javaType)) {
            return false;
        } else if ("java.lang.Long".equalsIgnoreCase(javaType)) {
            return false;
        }
        return true;
    }

    private static void extraSetterMethods(Class<?> clazz, List<Method> answer) {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (IntrospectionSupport.isSetter(m)) {
                answer.add(m);
            }
        }
    }

}
