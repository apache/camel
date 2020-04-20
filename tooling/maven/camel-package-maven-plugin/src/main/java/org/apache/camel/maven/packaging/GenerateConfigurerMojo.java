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
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

import static org.apache.camel.tooling.util.ReflectionHelper.doWithMethods;

/**
 * Generate configurer classes from @Configuer annotated classes.
 */
@Mojo(name = "generate-configurer", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GenerateConfigurerMojo extends AbstractGeneratorMojo {

    public static final DotName CONFIGURER = DotName.createSimple("org.apache.camel.spi.Configurer");

    /**
     * The output directory for generated java source code
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    /**
     * Whether to discover configurer classes from classpath by scanning for @Configurer annotations.
     * This requires using jandex-maven-plugin.
     */
    @Parameter(defaultValue = "true")
    protected boolean discoverClasses = true;

    /**
     * To generate configurer for these classes.
     * The syntax is either <tt>fqn</tt> or </tt>fqn=targetFqn</tt>.
     * This allows to map source class to target class to generate the source code using a different classname.
     */
    @Parameter
    protected List<String> classes;

    private DynamicClassLoader projectClassLoader;

    private static class Option extends BaseOptionModel {

        public Option(String name, Class type, String getter) {
            // we just use name, type
            setName(name);
            setJavaType(type.getName());
            setGetterMethod(getter);
        }
    }

    public GenerateConfigurerMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(project.getPackaging())) {
            return;
        }

        if (sourcesOutputDir == null) {
            sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        }
        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/generated/resources");
        }

        List<String> cp = new ArrayList<>();
        cp.add(0, project.getBuild().getOutputDirectory());
        project.getDependencyArtifacts().forEach(a -> {
            if (a.isResolved() && a.getFile() != null) {
                cp.add(a.getFile().getPath());
            }
        });
        projectClassLoader = DynamicClassLoader.createDynamicClassLoader(cp);

        Set<String> set = new LinkedHashSet<>();

        if (discoverClasses) {
            Path output = Paths.get(project.getBuild().getOutputDirectory());
            Index index;
            try (InputStream is = Files.newInputStream(output.resolve("META-INF/jandex.idx"))) {
                index = new IndexReader(is).read();
            } catch (IOException e) {
                throw new MojoExecutionException("IOException: " + e.getMessage(), e);
            }

            // discover all classes annotated with @Configurer
            List<AnnotationInstance> annotations = index.getAnnotations(CONFIGURER);
            annotations.stream()
                    .filter(annotation -> annotation.target().kind() == AnnotationTarget.Kind.CLASS)
                    .filter(annotation -> annotation.target().asClass().nestingType() == ClassInfo.NestingType.TOP_LEVEL)
                    .filter(annotation -> asBooleanDefaultTrue(annotation, "generateConfigurer"))
                    .forEach(annotation -> {
                        String currentClass = annotation.target().asClass().name().toString();
                        set.add(currentClass);
                    });
        }

        // additional classes
        if (classes != null && !classes.isEmpty()) {
            set.addAll(classes);
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Generating configuers for the following classes: " + set);
        }

        for (String fqn : set) {
            try {
                String targetFqn = fqn;
                int pos = fqn.indexOf('=');
                if (pos != -1) {
                    targetFqn = fqn.substring(pos + 1);
                    fqn = fqn.substring(0, pos);
                }
                List<Option> options = processClass(fqn);
                generateConfigurer(fqn, targetFqn, options);
                generateMetaInfConfigurer(targetFqn);
            } catch (Exception e) {
                throw new MojoExecutionException("Error processing class: " + fqn, e);
            }
        }
    }

    private List<Option> processClass(String fqn) throws ClassNotFoundException {
        List<Option> answer = new ArrayList<>();

        Class clazz = projectClassLoader.loadClass(fqn);
        // find all public setters
        doWithMethods(clazz, m -> {
            boolean setter = m.getName().length() >= 4 && m.getName().startsWith("set") && Character.isUpperCase(m.getName().charAt(3));
            setter &= Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 1;
            setter &= filterSetter(m);
            if (setter) {
                String getter = "get" + Character.toUpperCase(m.getName().charAt(3)) + m.getName().substring(4);
                Class type = m.getParameterTypes()[0];
                if (boolean.class == type || Boolean.class == type) {
                    try {
                        String isGetter = "is" + getter.substring(3);
                        clazz.getMethod(isGetter, null);
                        getter = isGetter;
                    } catch (Exception e) {
                        // ignore as its then assumed to be get
                    }
                }
                String t = Character.toUpperCase(m.getName().charAt(3)) + m.getName().substring(3 + 1);
                answer.add(new Option(t, type, getter));
            }
        });

        return answer;
    }

    private boolean filterSetter(Method setter) {
        // special for some
        if ("setBindingMode".equals(setter.getName())) {
            // we only want the string setter
            return setter.getParameterTypes()[0] == String.class;
        } else if ("setHostNameResolver".equals(setter.getName())) {
            // we only want the string setter
            return setter.getParameterTypes()[0] == String.class;
        }

        return true;
    }

    private void generateConfigurer(String fqn, String targetFqn, List<Option> options) throws IOException {
        int pos = targetFqn.lastIndexOf('.');
        String pn = targetFqn.substring(0, pos);
        String cn = targetFqn.substring(pos + 1) + "Configurer";
        String en = fqn;
        String pfqn = fqn;
        String psn = "org.apache.camel.support.component.PropertyConfigurerSupport";

        StringWriter sw = new StringWriter();
        PropertyConfigurerGenerator.generatePropertyConfigurer(pn, cn, en, pfqn, psn,
                false, false, options, sw);

        String source = sw.toString();

        String fileName = pn.replace('.', '/') + "/" + cn + ".java";
        sourcesOutputDir.mkdirs();
        boolean updated = updateResource(sourcesOutputDir.toPath(), fileName, source);
        if (updated) {
            getLog().info("Updated " + fileName);
        }
    }

    private void generateMetaInfConfigurer(String name) {
        int pos = name.lastIndexOf('.');
        String pn = name.substring(0, pos);
        String en = name.substring(pos + 1);
        try (Writer w = new StringWriter()) {
            w.append("# " + GENERATED_MSG + "\n");
            w.append("class=").append(pn).append(".").append(en).append("Configurer").append("\n");
            updateResource(resourcesOutputDir.toPath(), "META-INF/services/org/apache/camel/configurer/" + en, w.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean asBooleanDefaultTrue(AnnotationInstance ai, String name) {
        AnnotationValue av = ai.value(name);
        return av == null || av.asBoolean();
    }

}
