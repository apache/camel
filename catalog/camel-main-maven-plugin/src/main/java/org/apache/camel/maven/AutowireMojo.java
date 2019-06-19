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
package org.apache.camel.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.JSonSchemaHelper;
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
import org.reflections.Reflections;

/**
 * Pre scans your project and prepare autowiring by classpath scanning
 */
@Mojo(name = "autowire", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class AutowireMojo extends AbstractMainMojo {

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
    protected File outFolder;

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
        // perform common tasks
        super.execute();

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

        // find the autowire via classpath scanning
        List<String> autowires = findAutowireComponentOptionsByClasspath(catalog, camelComponentsOnClasspath, reflections, mappingProperties);

        if (!autowires.isEmpty()) {
            outFolder.mkdirs();
            File file = new File(outFolder, "autowire.properties");
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write("# Generated by camel build tools\n".getBytes());
                for (String line : autowires) {
                    fos.write(line.getBytes());
                    fos.write("\n".getBytes());
                }
                IOHelper.close(fos);
                getLog().info("Created file: " + file + " (autowire by classpath: " + autowires.size() + ")");
            } catch (Throwable e) {
                throw new MojoFailureException("Cannot write to file " + file + " due " + e.getMessage(), e);
            }
        }
    }

    protected Properties loadDefaultMappings() throws MojoFailureException {
        Properties mappings = new OrderedProperties();
        try {
            InputStream is = AutowireMojo.class.getResourceAsStream("/default-mappings.properties");
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

    protected List<String> findAutowireComponentOptionsByClasspath(CamelCatalog catalog, Set<String> components,
                                                                   Reflections reflections, Properties mappingProperties) {
        List<String> autowires = new ArrayList<>();

        for (String componentName : components) {
            getLog().debug("Autowiring Camel component: " + componentName);

            String json = catalog.componentJSonSchema(componentName);
            if (json == null) {
                getLog().debug("Cannot find component JSon metadata for component: " + componentName);
                continue;
            }

            List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
            Set<String> names = JSonSchemaHelper.getNames(rows);
            for (String name : names) {
                Map<String, String> row = JSonSchemaHelper.getRow(rows, name);
                String type = row.get("type");
                String javaType = safeJavaType(row.get("javaType"));
                if ("object".equals(type)) {
                    if (!isValidPropertyName(componentName, name)) {
                        getLog().debug("Skipping property name: " + name);
                        continue;
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
                                String line = "camel.component." + componentName + "." + name + "=#class:" + best.getName();
                                getLog().debug(line);
                                autowires.add(line);
                            }
                        }

                    } catch (Exception e) {
                        // ignore
                        getLog().debug("Cannot load class: " + name, e);
                    }
                }
            }
        }

        return autowires;
    }

    protected Class chooseBestKnownType(String componentName, String optionName, Class type, Set<Class<?>> candidates, Properties knownTypes) {
        String known = knownTypes.getProperty(type.getName());
        if (known != null) {
            for (String k : known.split(";")) {
                // special as we should skip this option
                if ("#skip#".equals(k)) {
                    return null;
                }
                Class found = candidates.stream().filter(c -> c.getName().equals(k)).findFirst().orElse(null);
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

    protected boolean isValidPropertyName(String componentName, String name) {
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

    protected boolean isValidAutowireClass(Class clazz) {
        // skip all from Apache Camel and regular JDK as they would be default anyway
        return !clazz.getName().startsWith("org.apache.camel");
    }

}
