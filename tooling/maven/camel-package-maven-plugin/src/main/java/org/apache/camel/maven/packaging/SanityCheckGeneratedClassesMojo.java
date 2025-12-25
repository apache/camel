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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

@Mojo(name = "sanity-check", threadSafe = true)
public class SanityCheckGeneratedClassesMojo extends AbstractGeneratorMojo {

    @Inject
    public SanityCheckGeneratedClassesMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // skip camel-api
        if ("camel-api".equals(project.getArtifactId())) {
            return;
        }

        Path generated = project.getBasedir().toPath().resolve("src/generated/java");
        if (Files.isDirectory(generated)) {
            try (Stream<Path> stream = Files.walk(generated)) {
                stream.filter(Files::isRegularFile)
                        .forEach(clazz -> {
                            String source = read(clazz);
                            if (!source.contains("@Generated")) {
                                getLog().warn("Generated class is missing @Generated: " + clazz);
                            }
                        });
            } catch (Exception e) {
                throw new MojoFailureException("Error sanity checking generated classes", e);
            }
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
