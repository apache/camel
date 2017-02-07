/**
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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.StringHelper.indentCollection;
import static org.apache.camel.maven.packaging.ValidateHelper.asName;
import static org.apache.camel.maven.packaging.ValidateHelper.validate;

/**
 * Validate a Camel component analyzing if the meta-data files for
 * <ul>
 *     <li>components</li>
 *     <li>dataformats</li>
 *     <li>languages</li>
 * </ul>
 * all contains the needed meta-data such as assigned labels, documentation for each option
 *
 * @goal validate-components
 */
public class ValidateComponentMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * Whether to validate if the components, data formats, and languages are properly documented and have all the needed details.
     *
     * @parameter default-value="true"
     */
    protected Boolean validate;

    /**
     * The output directory for generated components file
     *
     * @parameter default-value="${project.build.directory}/classes/"
     */
    protected File outDir;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * build context to check changed files and mark them for refresh
     * (used for m2e compatibility)
     *
     * @component
     * @readonly
     */
    private BuildContext buildContext;
    
    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the
     *                                                        threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!validate) {
            getLog().info("Validation disabled");
        } else {
            final Set<File> jsonFiles = new TreeSet<File>();
            PackageHelper.findJsonFiles(outDir, jsonFiles, new CamelComponentsFileFilter());
            boolean failed = false;

            for (File file : jsonFiles) {
                final String name = asName(file);
                final ErrorDetail detail = new ErrorDetail();

                getLog().debug("Validating file " + file);
                validate(file, detail);

                if (detail.hasErrors()) {
                    failed = true;
                    getLog().warn("The " + detail.getKind() + ": " + name + " has validation errors");
                    if (detail.isMissingDescription()) {
                        getLog().warn("Missing description on: " + detail.getKind());
                    }
                    if (detail.isMissingLabel()) {
                        getLog().warn("Missing label on: " + detail.getKind());
                    }
                    if (detail.isMissingSyntax()) {
                        getLog().warn("Missing syntax on endpoint");
                    }
                    if (detail.isMissingUriPath()) {
                        getLog().warn("Missing @UriPath on endpoint");
                    }
                    if (!detail.getMissingComponentDocumentation().isEmpty()) {
                        getLog().warn("Missing component documentation for the following options:" + indentCollection("\n\t", detail.getMissingComponentDocumentation()));
                    }
                    if (!detail.getMissingEndpointDocumentation().isEmpty()) {
                        getLog().warn("Missing endpoint documentation for the following options:" + indentCollection("\n\t", detail.getMissingEndpointDocumentation()));
                    }
                }
            }

            if (failed) {
                throw new MojoFailureException("Validating failed, see errors above!");
            } else {
                getLog().info("Validation complete");
            }
        }
    }

    private class CamelComponentsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no components there
                return false;
            }

            // skip connector metadata
            if ("camel-connector-schema.json".equals(pathname.getName()) || "camel-component-schema.json".equals(pathname.getName())) {
                return false;
            }

            if (pathname.isFile() && pathname.getName().endsWith(".json")) {
                // must be a components json file
                try {
                    String json = loadText(new FileInputStream(pathname));
                    return json != null && json.contains("\"kind\": \"component\"");
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("component.properties"));
        }
    }

}
