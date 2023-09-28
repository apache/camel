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
package org.apache.camel.maven.sync.properties;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Copy the properties {@link #sourcePomXml} to {@link #targetPomXml}, applying filters defined in
 * {@link #propertyIncludes} and {@link #propertyExcludes}.
 */
@Mojo(name = "sync-properties", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class SyncPropertiesMojo extends AbstractMojo {

    /**
     * The path to {@code camel-parent} {@code pom.xml}
     *
     * @since 4.0.0
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}/parent/pom.xml", property = "camel.camelParentPomXml")
    private File sourcePomXml;

    /**
     * The path to the generated {@code camel-dependencies} {@code pom.xml} file that will be installed and deployed
     * instead of the {@code camel-dependencies} {@code pom.xml} file available in the source tree.
     *
     * @since 4.0.0
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-pom.xml",
               property = "camel.targetPomXml")
    private File targetPomXml;

    /**
     * The path to the root {@code pom.xml} of the Camel source tree
     *
     * @since 4.0.0
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}/pom.xml", property = "camel.camelPomXml")
    private File camelPomXml;

    /**
     * The encoding to read and write files
     *
     * @since 4.0.0
     */
    @Parameter(defaultValue = "${project.build.sourceEncoding}", property = "camel.encoding")
    private String encoding;

    /**
     * The version of the current Maven module
     *
     * @since 4.0.0
     */
    @Parameter(defaultValue = "${project.version}")
    private String version;

    /**
     * List of regular expressions to select properties from {@link #sourcePomXml}
     *
     * @since 4.0.0
     */
    @Parameter(defaultValue = ".*-version")
    private List<String> propertyIncludes;

    /**
     * List of regular expressions to ignore from {@link #sourcePomXml}
     *
     * @since 4.0.0
     */
    @Parameter
    private List<String> propertyExcludes;

    /**
     * The Maven project.
     *
     * @since 4.0.0
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Path camelParentPomXmlPath = sourcePomXml.toPath();
        if (!Files.isRegularFile(camelParentPomXmlPath)) {
            throw new MojoExecutionException("camelParentPomXml " + sourcePomXml + " does not exist");
        }
        final Path resultPath = targetPomXml.toPath();
        final Path camelPomXmlPath = camelPomXml.toPath();
        if (!Files.isRegularFile(camelPomXmlPath)) {
            throw new MojoExecutionException("camelPomXml " + camelPomXml + " does not exist");
        }
        final Charset charset = Charset.forName(encoding);

        final Model camelParentPomXmlModel;
        try (Reader r = Files.newBufferedReader(camelParentPomXmlPath, charset)) {
            camelParentPomXmlModel = new org.apache.maven.model.io.xpp3.MavenXpp3Reader().read(r);
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException("Could not parse " + camelParentPomXmlPath, e);
        }

        final Model camelPomXmlModel;
        try (Reader r = Files.newBufferedReader(camelPomXmlPath, charset)) {
            camelPomXmlModel = new org.apache.maven.model.io.xpp3.MavenXpp3Reader().read(r);
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException("Could not parse " + camelPomXmlPath, e);
        }

        final String template;
        try (InputStream in = SyncPropertiesMojo.class.getResourceAsStream("/camel-dependencies-template.xml");
             Reader r = new InputStreamReader(in, charset)) {
            template = IOHelper.toString(r);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read camel-dependencies-template.xml from class path", e);
        }

        final Predicate<String> includes = toPredicate(propertyIncludes, true);
        final Predicate<String> excludes = toPredicate(propertyExcludes, false);
        final String properties = Stream.concat(
                camelParentPomXmlModel.getProperties().entrySet().stream(),
                camelPomXmlModel.getProperties().entrySet().stream()
                        .filter(property -> property.getKey().equals("license-maven-plugin-version")))
                .filter(property -> includes.test((String) property.getKey()) && !excludes.test((String) property.getKey()))
                .map(property -> "<" + property.getKey() + ">" + property.getValue() + "</" + property.getKey() + ">")
                .sorted()
                .collect(Collectors.joining("\n        "));

        try {
            final String camelPropertiesContent = template
                    .replace("@version@", version)
                    .replace("@properties@", properties);

            // write lines
            boolean updated = FileUtil.updateFile(resultPath, camelPropertiesContent, charset);
            if (updated) {
                getLog().info("Updated: " + resultPath);
            }
            getLog().debug("Finished.");

            project.setPomFile(resultPath.toFile());

        } catch (IOException ex) {
            throw new MojoExecutionException("Could not write to " + resultPath, ex);
        }
    }

    static Predicate<String> toPredicate(List<String> regularExpressions, boolean defaultResult) {
        if (regularExpressions == null || regularExpressions.isEmpty()) {
            return key -> defaultResult;
        } else {
            final List<Pattern> patterns = regularExpressions.stream()
                    .map(Pattern::compile)
                    .toList();
            return key -> patterns.stream().anyMatch(pattern -> pattern.matcher(key).matches());
        }
    }

}
