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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Updates the EagerClassloadedHelper.java with the class names to eager load when using camel-main.
 */
@Mojo(name = "update-classloaded-helper", threadSafe = true)
public class UpdateEagerClassloadedHelper extends AbstractGeneratorMojo {

    public static final DotName EAGER_CLASSLOADED = DotName.createSimple("org.apache.camel.spi.annotations.EagerClassloaded");

    private static final String[] MODULES = new String[] { "camel-base-engine", "camel-core-processor", "camel-support" };

    private static final String START_TOKEN = "// EAGER-CLASSLOADED: START";
    private static final String END_TOKEN = "// EAGER-CLASSLOADED: END";

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File mainDir = findCamelDirectory(baseDir, "core/camel-main");
        if (mainDir == null) {
            getLog().debug("No core/camel-main folder found, skipping execution");
            return;
        }

        Set<String> fqns = new TreeSet<>();
        // discover classes from a set of known core modules
        for (String p : MODULES) {
            File dir = findCamelDirectory(baseDir, "core/" + p);
            if (dir != null && dir.exists() && dir.isDirectory()) {
                Path output = Paths.get(dir.getAbsolutePath() + "/target/classes");
                discoverClasses(output, fqns);
            }
        }

        if (fqns.isEmpty()) {
            return;
        }

        getLog().info("There are " + fqns.size()
                      + " classes to eager loaded across the Camel core modules");
        try {
            boolean updated = updateHelper(mainDir, fqns);
            if (updated) {
                getLog().info("Updated camel-main/src/main/java/org/apache/camel/main/EagerClassloadedHelper.java file");
            } else {
                getLog().debug("No changes to camel-main/src/main/java/org/apache/camel/main/EagerClassloadedHelper.java file");
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error updating EagerClassloadedHelper.java", e);
        }
    }

    private void discoverClasses(Path output, Set<String> fqns) {
        Index index;
        try (InputStream is = Files.newInputStream(output.resolve("META-INF/jandex.idx"))) {
            index = new IndexReader(is).read();
        } catch (IOException e) {
            // ignore
            return;
        }

        // discover all classes annotated with @EagerClassloaded
        List<AnnotationInstance> annotations = index.getAnnotations(EAGER_CLASSLOADED);
        annotations.stream()
                .filter(annotation -> annotation.target().kind() == AnnotationTarget.Kind.CLASS)
                .filter(annotation -> annotation.target().asClass().nestingType() == ClassInfo.NestingType.TOP_LEVEL)
                .forEach(annotation -> {
                    String fqn = annotation.target().asClass().name().toString();
                    fqns.add(fqn);
                });
    }

    private boolean updateHelper(File camelDir, Set<String> fqns) throws Exception {
        // load source code and update
        File java = new File(camelDir, "src/main/java/org/apache/camel/main/EagerClassloadedHelper.java");
        String text = PackageHelper.loadText(java);
        String spaces8 = "        ";

        StringJoiner sb = new StringJoiner("\n");
        sb.add(spaces8 + "count = " + fqns.size() + ";");
        for (String name : fqns) {
            sb.add(spaces8 + name + ".onClassloaded(LOG);");
        }
        String changed = sb.toString();

        String existing = Strings.between(text, START_TOKEN, END_TOKEN);
        if (existing != null) {
            // remove leading line breaks etc
            existing = existing.trim();
            changed = changed.trim();
            if (existing.equals(changed)) {
                return false;
            } else {
                String before = Strings.before(text, START_TOKEN);
                String after = Strings.after(text, END_TOKEN);
                text = before + START_TOKEN + "\n" + spaces8 + changed + "\n" + spaces8 + END_TOKEN + after;
                PackageHelper.writeText(java, text);
                return true;
            }
        }

        return false;
    }

}
