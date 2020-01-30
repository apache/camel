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
package org.apache.maven.plugins.javadoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;

/**
 * Bundles the Javadoc documentation for <code>main Java code</code> in an
 * <b>NON aggregator</b> project into a jar using the standard <a href=
 * "http://docs.oracle.com/javase/7/docs/technotes/guides/javadoc/">Javadoc
 * Tool</a>.
 *
 * @version $Id: JavadocJar.java 1752018 2016-07-09 16:35:25Z rfscholte $
 * @since 2.0
 */
@Mojo(name = "jar", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class CamelJavadocJar extends AbstractJavadocMojo {
    /**
     * Includes all generated Javadoc files
     */
    private static final String[] DEFAULT_INCLUDES = new String[] {"**/**"};

    /**
     * Excludes all processing files.
     *
     * @see AbstractJavadocMojo#DEBUG_JAVADOC_SCRIPT_NAME
     * @see AbstractJavadocMojo#OPTIONS_FILE_NAME
     * @see AbstractJavadocMojo#PACKAGES_FILE_NAME
     * @see AbstractJavadocMojo#ARGFILE_FILE_NAME
     * @see AbstractJavadocMojo#FILES_FILE_NAME
     */
    private static final String[] DEFAULT_EXCLUDES = new String[] {DEBUG_JAVADOC_SCRIPT_NAME, OPTIONS_FILE_NAME, PACKAGES_FILE_NAME, ARGFILE_FILE_NAME, FILES_FILE_NAME};

    // ----------------------------------------------------------------------
    // Mojo components
    // ----------------------------------------------------------------------

    /**
     * Specifies the destination directory where javadoc saves the generated
     * HTML files. <br>
     * 
     * @see <a href=
     *      "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#d">d</a>
     *      option
     */
    @Parameter(defaultValue = "${project.build.directory}/apidocstmp", required = true)
    protected File intermediateDirectory;

    /**
     * Used for attaching the artifact in the project.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The Jar archiver.
     *
     * @since 2.5
     */
    @Component(role = Archiver.class, hint = "jar")
    private JarArchiver jarArchiver;

    // ----------------------------------------------------------------------
    // Mojo Parameters
    // ----------------------------------------------------------------------

    /**
     * Specifies the destination directory where javadoc saves the generated
     * HTML files. See <a href=
     * "http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#d">d</a>.
     *
     * @deprecated
     */
    @Deprecated
    @Parameter(property = "destDir")
    private File destDir;

    /**
     * Specifies the directory where the generated jar file will be put.
     */
    @Parameter(property = "project.build.directory")
    private String jarOutputDirectory;

    /**
     * Specifies the filename that will be used for the generated jar file.
     * Please note that <code>-javadoc</code> or <code>-test-javadoc</code> will
     * be appended to the file name.
     */
    @Parameter(property = "project.build.finalName")
    private String finalName;

    /**
     * Specifies whether to attach the generated artifact to the project helper.
     * <br/>
     */
    @Parameter(property = "attach", defaultValue = "true")
    private boolean attach;

    /**
     * The archive configuration to use. See
     * <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven
     * Archiver Reference</a>.
     *
     * @since 2.5
     */
    @Parameter
    private MavenArchiveConfiguration archive = new JavadocArchiveConfiguration() {
        {
            setForced(false);
        }
    };

    /**
     * Path to the default MANIFEST file to use. It will be used if
     * <code>useDefaultManifestFile</code> is set to <code>true</code>.
     *
     * @since 2.5
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/MANIFEST.MF", required = true, readonly = true)
    private File defaultManifestFile;

    /**
     * Set this to <code>true</code> to enable the use of the
     * <code>defaultManifestFile</code>. <br/>
     *
     * @since 2.5
     */
    @Parameter(defaultValue = "false")
    private boolean useDefaultManifestFile;

    /**
     * @since 2.10
     */
    @Parameter(property = "maven.javadoc.classifier", defaultValue = "javadoc", required = true)
    private String classifier;

    /** {@inheritDoc} */
    @Override
    public void doExecute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping javadoc generation");
            return;
        }

        try {
            Field f = AbstractJavadocMojo.class.getDeclaredField("additionalOptions");
            f.setAccessible(true);
            String[] additionalOptions = (String[])f.get(this);
            if (additionalOptions == null || additionalOptions.length == 0) {
                additionalOptions = new String[] {"-notimestamp"};
            } else {
                List<String> l = new ArrayList<>(Arrays.asList(additionalOptions));
                l.add("-notimestamp");
                additionalOptions = l.toArray(new String[0]);
            }
            f.set(this, additionalOptions);
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to set javadoc options", e);
        }

        File innerDestDir = this.destDir;
        if (innerDestDir == null) {
            innerDestDir = new File(getOutputDirectory());
        }

        if (!("pom".equalsIgnoreCase(project.getPackaging()) && isAggregator())) {
            ArtifactHandler artifactHandler = project.getArtifact().getArtifactHandler();
            if (!"java".equals(artifactHandler.getLanguage())) {
                getLog().info("Not executing Javadoc as the project is not a Java classpath-capable package");
                return;
            }
        }

        try {
            executeReport(Locale.getDefault());
        } catch (MavenReportException e) {
            failOnError("MavenReportException: Error while generating Javadoc", e);
        } catch (RuntimeException e) {
            failOnError("RuntimeException: Error while generating Javadoc", e);
        }

        if (innerDestDir.exists()) {
            try {
                Path inputDir = innerDestDir.toPath();
                Path outputDir = intermediateDirectory.toPath();
                Files.createDirectories(outputDir);
                Set<Path> input = getAllRelativeFiles(inputDir);
                Set<Path> output = getAllRelativeFiles(outputDir);
                // remove deleted files
                output.removeAll(input);
                output.remove(Paths.get("META-INF"));
                output.remove(Paths.get("META-INF/MANIFEST.MF"));
                for (Path p : output) {
                    Files.delete(outputDir.resolve(p));
                }
                // copy all files
                for (Path p : input) {
                    copy(inputDir.resolve(p), outputDir.resolve(p));
                }

                File outputFile = generateArchive(intermediateDirectory, finalName + "-" + getClassifier() + ".jar");

                if (!attach) {
                    getLog().info("NOT adding javadoc to attached artifacts list.");
                } else {
                    // TODO: these introduced dependencies on the project are
                    // going to become problematic - can we export it
                    // through metadata instead?
                    projectHelper.attachArtifact(project, "javadoc", getClassifier(), outputFile);
                }
            } catch (ArchiverException e) {
                failOnError("ArchiverException: Error while creating archive", e);
            } catch (IOException e) {
                failOnError("IOException: Error while creating archive", e);
            } catch (RuntimeException e) {
                failOnError("RuntimeException: Error while creating archive", e);
            }
        }
    }

    /*
     * private void writeIncrementalInfo(MavenProject project) throws
     * MojoExecutionException { try { Path cacheData =
     * getIncrementalDataPath(project); String curdata = getIncrementalData();
     * Files.createDirectories(cacheData.getParent()); try (Writer w =
     * Files.newBufferedWriter(cacheData)) { w.append(curdata); } } catch
     * (IOException e) { throw new
     * MojoExecutionException("Error checking manifest uptodate status", e); } }
     * private boolean isUpToDate(MavenProject project) throws
     * MojoExecutionException { long t0 = System.currentTimeMillis(); try { Path
     * cacheData = getIncrementalDataPath(project); String prvdata; if
     * (Files.isRegularFile(cacheData)) { prvdata = new
     * String(Files.readAllBytes(cacheData), StandardCharsets.UTF_8); } else {
     * prvdata = null; } String curdata = getIncrementalData(); if
     * (curdata.equals(prvdata)) { long lastmod =
     * Files.getLastModifiedTime(cacheData).toMillis(); Set<String> stale =
     * Stream.concat(Stream.of(new
     * File(project.getBuild().getOutputDirectory())),
     * project.getArtifacts().stream().map(Artifact::getFile)) .flatMap(f ->
     * newer(lastmod, f)) .collect(Collectors.toSet()); if (!stale.isEmpty()) {
     * getLog().info("Stale files: " + stale.stream()
     * .collect(Collectors.joining(", "))); } else { // everything is in order,
     * skip
     * getLog().info("Skipping manifest generation, everything is up to date.");
     * return true; } } else { if (prvdata == null) {
     * getLog().info("No previous run data found, generating manifest."); } else
     * { getLog().info("Configuration changed, re-generating manifest."); } } }
     * catch (IOException e) { throw new
     * MojoExecutionException("Error checking manifest uptodate status", e); }
     * finally { long t1 = System.currentTimeMillis();
     * getLog().warn("isUpToDate took " + (t1 - t0) + " ms"); } return false; }
     * private String getIncrementalData() { return
     * getInstructions().entrySet().stream().map(e -> e.getKey() + "=" +
     * e.getValue()) .collect(Collectors.joining("\n", "", "\n")); } private
     * Path getIncrementalDataPath(MavenProject project) { return
     * Paths.get(project.getBuild().getDirectory(), "camel-javadoc-plugin",
     * "org.apache.camel_camel-javadoc-plugin_javadoc_xx"); } private long
     * lastmod(Path p) { try { return Files.getLastModifiedTime(p).toMillis(); }
     * catch (IOException e) { return 0; } } private Stream<String> newer(long
     * lastmod, File file) { try { if (file.isDirectory()) { return
     * Files.walk(file.toPath()) .filter(Files::isRegularFile) .filter(p ->
     * lastmod(p) > lastmod) .map(Path::toString); } else if (file.isFile()) {
     * if (lastmod(file.toPath()) > lastmod) { if
     * (file.getName().endsWith(".jar")) { try (ZipFile zf = new ZipFile(file))
     * { return zf.stream() .filter(ze -> !ze.isDirectory()) .filter(ze ->
     * ze.getLastModifiedTime().toMillis() > lastmod) .map(ze -> file.toString()
     * + "!" + ze.getName()) .collect(Collectors.toList()) .stream(); } } else {
     * return Stream.of(file.toString()); } } else { return Stream.empty(); } }
     * else { return Stream.empty(); } } catch (IOException e) { throw new
     * IOError(e); } }
     */

    void copy(Path in, Path out) throws IOException {
        if (Files.isDirectory(in)) {
            Files.createDirectories(out);
        } else if (Files.isRegularFile(in)) {
            byte[] dataIn = Files.readAllBytes(in);
            if (Files.isRegularFile(out) && Files.isReadable(out)) {
                byte[] dataOut = Files.readAllBytes(out);
                if (Arrays.equals(dataIn, dataOut)) {
                    return;
                }
            }
            Files.createDirectories(out.getParent());
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    TreeSet<Path> getAllRelativeFiles(Path dir) throws IOException {
        return Files.walk(dir).map(dir::relativize).collect(Collectors.toCollection(TreeSet::new));
    }

    // ----------------------------------------------------------------------
    // Protected methods
    // ----------------------------------------------------------------------

    /**
     * @return the wanted classifier, i.e. <code>javadoc</code> or
     *         <code>test-javadoc</code>
     */
    protected String getClassifier() {
        return classifier;
    }

    // ----------------------------------------------------------------------
    // private methods
    // ----------------------------------------------------------------------

    /**
     * Method that creates the jar file
     *
     * @param javadocFiles the directory where the generated jar file will be
     *            put
     * @param jarFileName the filename of the generated jar file
     * @return a File object that contains the generated jar file
     * @throws ArchiverException {@link ArchiverException}
     * @throws IOException {@link IOException}
     */
    private File generateArchive(File javadocFiles, String jarFileName) throws ArchiverException, IOException {
        File javadocJar = new File(jarOutputDirectory, jarFileName);

        MavenArchiver archiver = new MavenArchiver();
        archiver.setArchiver(jarArchiver);
        archiver.setOutputFile(javadocJar);

        File contentDirectory = javadocFiles;
        if (!contentDirectory.exists()) {
            getLog().warn("JAR will be empty - no content was marked for inclusion!");
        } else {
            archiver.getArchiver().addDirectory(contentDirectory, DEFAULT_INCLUDES, DEFAULT_EXCLUDES);
        }

        List<Resource> resources = project.getBuild().getResources();

        for (Resource r : resources) {
            if (r.getDirectory().endsWith("maven-shared-archive-resources")) {
                archiver.getArchiver().addDirectory(new File(r.getDirectory()));
            }
        }

        if (useDefaultManifestFile && defaultManifestFile.exists() && archive.getManifestFile() == null) {
            getLog().info("Adding existing MANIFEST to archive. Found under: " + defaultManifestFile.getPath());
            archive.setManifestFile(defaultManifestFile);
        }

        if (archive.getManifestFile() == null) {
            try {

                Manifest manifest = archiver.getManifest(session, project, archive);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                manifest.write(baos);
                Path man = javadocFiles.toPath().resolve("META-INF/MANIFEST.MF");
                byte[] data = null;
                if (Files.isRegularFile(man)) {
                    data = Files.readAllBytes(man);
                }
                if (!Arrays.equals(data, baos.toByteArray())) {
                    Files.createDirectories(man.getParent());
                    Files.copy(new ByteArrayInputStream(baos.toByteArray()), man, StandardCopyOption.REPLACE_EXISTING);
                }
                archive.setManifestFile(man.toFile());
            } catch (ManifestException e) {
                throw new ArchiverException("ManifestException: " + e.getMessage(), e);
            } catch (DependencyResolutionRequiredException e) {
                throw new ArchiverException("DependencyResolutionRequiredException: " + e.getMessage(), e);
            }
        }

        try {
            archiver.createArchive(session, project, archive);
        } catch (ManifestException e) {
            throw new ArchiverException("ManifestException: " + e.getMessage(), e);
        } catch (DependencyResolutionRequiredException e) {
            throw new ArchiverException("DependencyResolutionRequiredException: " + e.getMessage(), e);
        }

        return javadocJar;
    }
}
