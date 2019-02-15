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
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor information for easier auto-discovery in Camel.
 */
@Mojo(name = "generate-jaxb-list", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class PackageJaxbMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.build.directory}/generated/camel/jaxb")
    protected File jaxbIndexOutDir;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                 threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<String> locations = new ArrayList<>();
        locations.add(project.getBuild().getOutputDirectory());

        processClasses(createIndex(locations));

        Path path = jaxbIndexOutDir.toPath();
        addResourceDirectory(path);
    }

    private void processClasses(IndexView index) {
        Map<String, Set<String>> byPackage = new HashMap<>();

        Stream.of(XmlRootElement.class, XmlEnum.class)
                .map(Class::getName)
                .map(DotName::createSimple)
                .map(index::getAnnotations)
                .flatMap(Collection::stream)
                .map(AnnotationInstance::target)
                .map(AnnotationTarget::asClass)
                .map(ClassInfo::name)
                .map(DotName::toString)
                .forEach(name -> {
                    int idx = name.lastIndexOf('.');
                    String p = name.substring(0, idx);
                    String c = name.substring(idx + 1);
                    byPackage.computeIfAbsent(p, s -> new TreeSet<>()).add(c);
                });

        Path jaxbIndexDir = jaxbIndexOutDir.toPath();
        int count = 0;
        for (Map.Entry<String, Set<String>> entry : byPackage.entrySet()) {
            Path file = jaxbIndexDir.resolve(entry.getKey().replace('.', '/')).resolve("jaxb.index");
            StringBuilder sb = new StringBuilder();
            sb.append("# " + GENERATED_MSG + NL);
            for (String s : entry.getValue()) {
                sb.append(s);
                sb.append(NL);
            }
            updateResource(file, sb.toString());
        }

        getLog().info("Generated " + jaxbIndexOutDir + " containing " + count + " jaxb.index elements");
    }

    private IndexView createIndex(List<String> locations) throws MojoExecutionException {
        try {
            Indexer indexer = new Indexer();
            locations.stream()
                    .map(this::asFolder)
                    .filter(Files::isDirectory)
                    .flatMap(this::walk)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(p -> index(indexer, p));
            return indexer.complete();
        } catch (IOError e) {
            throw new MojoExecutionException("Error", e);
        }
    }

    private Path asFolder(String p) {
        if (p.endsWith(".jar")) {
            File fp = new File(p);
            try {
                Map<String, String> env = new HashMap<>();
                return FileSystems.newFileSystem(URI.create("jar:" + fp.toURI().toString()), env).getPath("/");
            } catch (FileSystemAlreadyExistsException e) {
                return FileSystems.getFileSystem(URI.create("jar:" + fp.toURI().toString())).getPath("/");
            } catch (IOException e) {
                throw new IOError(e);
            }
        } else {
            return Paths.get(p);
        }
    }

    private Stream<Path> walk(Path p) {
        try {
            return Files.walk(p);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private void index(Indexer indexer, Path p) {
        try (InputStream is = Files.newInputStream(p)) {
            indexer.index(is);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

}
