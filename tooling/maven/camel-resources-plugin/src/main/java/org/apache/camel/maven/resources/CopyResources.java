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
package org.apache.camel.maven.resources;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenReaderFilter;

@Mojo(name = "copy-resources", threadSafe = true)
public class CopyResources extends AbstractMojo {

    public static final List<String> NON_FILTERED_EXTENSIONS =
            Collections.unmodifiableList(Arrays.asList("jpg", "jpeg", "gif", "bmp", "png"));

    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    @Parameter
    private String encoding = "UTF-8";

    /**
     * The output directory into which to copy the resources.
     */
    @Parameter(required = true)
    private File outputDirectory;

    /**
     * The list of resources we want to transfer. See the Maven Model for a
     * description of how to code the resources element.
     */
    @Parameter(required = true)
    private List<Resource> resources;

    @Component
    private MavenSession session;

    @Component
    private MavenReaderFilter readerFilter;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Path output = outputDirectory.toPath();
            char[] buf = new char[8192];
            StringBuilder sb = new StringBuilder(8192);
            for (Resource resource : resources) {
                Path dir = resource.getDirectory().toPath();
                List<Path> files = Files.walk(dir).filter(Files::isRegularFile).collect(Collectors.toList());
                for (Path file : files) {
                    boolean filtering = isFiltering(resource, file);
                    if (filtering) {
                        try (Reader reader = Files.newBufferedReader(file, Charset.forName(encoding))) {
                            Reader wrapped = readerFilter.filter(reader, true, project,
                                    null, false, session);
                            sb.setLength(0);
                            while (true) {
                                int l = wrapped.read(buf, 0, buf.length);
                                if (l >= 0) {
                                    sb.append(buf, 0, l);
                                } else {
                                    break;
                                }
                            }
                            String content = sb.toString();
                            FileUtil.updateFile(output.resolve(dir.relativize(file)), content);
                        } catch (IOException e) {
                            throw new IOException("Error processing resource: " + file, e);
                        }
                    } else {
                        FileUtil.updateFile(file, output.resolve(dir.relativize(file)));
                    }
                }
            }
        } catch (IOException | MavenFilteringException e) {
            throw new MojoFailureException("Unable to copy resources", e);
        }
    }

    protected boolean isFiltering(Resource resource, Path file) {
        if (resource.isFiltering()) {
            String ext = Strings.after(file.getFileName().toString(), ".");
            return ext == null || !NON_FILTERED_EXTENSIONS.contains(ext);
        }
        return false;
    }

}
