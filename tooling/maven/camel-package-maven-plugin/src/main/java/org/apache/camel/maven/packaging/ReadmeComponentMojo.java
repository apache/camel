/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Generate or updates the component readme.md file in the project root directort.
 *
 * @goal update-readme
 */
public class ReadmeComponentMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The output directory for generated readme file
     *
     * @parameter default-value="${project.build.directory}"
     */
    protected File buildDir;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     *
     * @component
     * @readonly
     */
    private BuildContext buildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File readmeDir = new File(buildDir, "..");
        File readmeFile = new File(readmeDir, "readme.md");

        // see if a file with name readme.md exists in any kind of case
        String[] names = readmeDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return "readme.md".equalsIgnoreCase(name);
            }
        });
        if (names != null && names.length == 1) {
            readmeFile = new File(readmeDir, names[0]);
        }

        boolean exists = readmeFile.exists();
        if (exists) {
            getLog().info("Using existing " + readmeFile.getName() + " file");
        } else {
            getLog().info("Creating new readme.md file");
        }

        try {
            OutputStream os = buildContext.newFileOutputStream(readmeFile);
            os.write("Hello World".getBytes());
            os.close();

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write to " + readmeFile + ". Reason: " + e, e);
        }

    }


}
