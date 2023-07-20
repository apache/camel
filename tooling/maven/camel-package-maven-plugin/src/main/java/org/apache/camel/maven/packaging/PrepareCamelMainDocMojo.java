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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.mvel2.templates.TemplateRuntime;

/**
 * Prepares camel-main by updating main documentation.
 */
@Mojo(name = "prepare-main", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class PrepareCamelMainDocMojo extends AbstractGeneratorMojo {

    /**
     * The documentation directory
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/docs")
    protected File docDocDir;

    /**
     * The documentation directory
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/META-INF/camel-main-configuration-metadata.json")
    protected File mainJsonFile;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        docDocDir = new File(project.getBasedir(), "src/main/docs");
        mainJsonFile
                = new File(project.getBasedir(), "src/generated/resources/META-INF/camel-main-configuration-metadata.json");
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!mainJsonFile.exists()) {
            // its not this module so skip
            return;
        }

        File file = new File(docDocDir, "main.adoc");
        boolean exists = file.exists();
        boolean updated;
        try {
            String json = PackageHelper.loadText(mainJsonFile);
            MainModel model = JsonMapper.generateMainModel(json);
            String options = evaluateTemplate("main-options.mvel", model);
            updated = updateOptionsIn(file, "main", options);
        } catch (IOException e) {
            throw new MojoExecutionException("Error preparing main docs", e);
        }

        if (updated) {
            getLog().info("Updated doc file: " + file);
        } else if (exists) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("No changes to doc file: " + file);
            }
        } else {
            getLog().warn("No main doc file: " + file);
        }
    }

    private static String evaluateTemplate(final String templateName, final MainModel model) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();

        try (InputStream templateStream = UpdateReadmeMojo.class.getClassLoader().getResourceAsStream(templateName)) {
            String template = PackageHelper.loadText(templateStream);
            // loop each group and eval
            for (MainModel.MainGroupModel group : model.getGroups()) {
                Map<String, Object> root = new HashMap<>();
                root.put("group", group);
                root.put("options", model.getOptions().stream()
                        .filter(o -> o.getName().startsWith(group.getName()))
                        .collect(Collectors.toList()));
                String eval
                        = (String) TemplateRuntime.eval(template, root, Collections.singletonMap("util", MvelHelper.INSTANCE));
                sb.append(eval);
                sb.append("\n");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error processing mvel template `" + templateName + "`", e);
        }

        return sb.toString();
    }

    private boolean updateOptionsIn(final File file, final String kind, final String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        final String updated = changed.trim();
        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, "// " + kind + " options: START", "// " + kind + " options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                if (existing.equals(updated)) {
                    return false;
                }

                String before = Strings.before(text, "// " + kind + " options: START");
                String after = Strings.after(text, "// " + kind + " options: END");
                text = before + "// " + kind + " options: START\n" + updated + "\n// " + kind + " options: END" + after;
                PackageHelper.writeText(file, text);
                return true;
            }

            getLog().warn("Cannot find markers in file " + file);
            getLog().warn("Add the following markers");
            getLog().warn("\t// " + kind + " options: START");
            getLog().warn("\t// " + kind + " options: END");
            return false;
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

}
