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
package org.apache.felix.bundleplugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Instructions;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;
import aQute.lib.collections.ExtList;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Scanner;
import org.osgi.service.metatype.MetaTypeService;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Generate an OSGi manifest for this project
 */
@Mojo(name = "manifest", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ManifestPlugin extends BundlePlugin {
    /**
     * When true, generate the manifest by rebuilding the full bundle in memory
     */
    @Parameter(property = "rebuildBundle")
    protected boolean rebuildBundle;

    /**
     * When true, manifest generation on incremental builds is supported in IDEs
     * like Eclipse. Please note that the underlying BND library does not
     * support incremental build, which means always the whole manifest and SCR
     * metadata is generated.
     */
    @Parameter(property = "supportIncrementalBuild")
    private boolean supportIncrementalBuild;

    @Component
    private BuildContext buildContext;

    @Parameter(defaultValue = "${showStaleFiles}")
    private boolean showStaleFiles;

    @Override
    protected void execute(Map<String, String> instructions, ClassPathItem[] classpath) throws MojoExecutionException {

        if (supportIncrementalBuild && isUpToDate(project)) {
            return;
        }
        // in incremental build execute manifest generation only when explicitly
        // activated
        // and when any java file was touched since last build
        if (buildContext.isIncremental() && !(supportIncrementalBuild && anyJavaSourceFileTouchedSinceLastBuild())) {
            getLog().debug("Skipping manifest generation because no java source file was added, updated or removed since last build.");
            return;
        }

        Analyzer analyzer;
        try {
            analyzer = getAnalyzer(project, instructions, classpath);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Cannot find " + e.getMessage() + " (manifest goal must be run after compile phase)", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error trying to generate Manifest", e);
        } catch (MojoFailureException e) {
            getLog().error(e.getLocalizedMessage());
            throw new MojoExecutionException("Error(s) found in manifest configuration", e);
        } catch (Exception e) {
            getLog().error("An internal error occurred", e);
            throw new MojoExecutionException("Internal error in maven-bundle-plugin", e);
        }

        File outputFile = new File(manifestLocation, "MANIFEST.MF");

        try {
            writeManifest(analyzer, outputFile, niceManifest, exportScr, scrLocation, buildContext, getLog());

            if (supportIncrementalBuild) {
                writeIncrementalInfo(project);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error trying to write Manifest to file " + outputFile, e);
        } finally {
            try {
                analyzer.close();
            } catch (IOException e) {
                throw new MojoExecutionException("Error trying to write Manifest to file " + outputFile, e);
            }
        }
    }

    /**
     * Checks if any *.java file was added, updated or removed since last build
     * in any source directory.
     */
    private boolean anyJavaSourceFileTouchedSinceLastBuild() {
        @SuppressWarnings("unchecked")
        List<String> sourceDirectories = project.getCompileSourceRoots();
        for (String sourceDirectory : sourceDirectories) {
            File directory = new File(sourceDirectory);
            Scanner scanner = buildContext.newScanner(directory);
            Scanner deleteScanner = buildContext.newDeleteScanner(directory);
            if (containsJavaFile(scanner) || containsJavaFile(deleteScanner)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsJavaFile(Scanner scanner) {
        String[] includes = new String[] {"**/*.java"};
        scanner.setIncludes(includes);
        scanner.scan();
        return scanner.getIncludedFiles().length > 0;
    }

    public Manifest getManifest(MavenProject project, ClassPathItem[] classpath) throws IOException, MojoFailureException, MojoExecutionException, Exception {
        return getManifest(project, new LinkedHashMap<String, String>(), classpath, buildContext);
    }

    public Manifest getManifest(MavenProject project, Map<String, String> instructions, ClassPathItem[] classpath, BuildContext buildContext)
        throws IOException, MojoFailureException, MojoExecutionException, Exception {
        Analyzer analyzer = getAnalyzer(project, instructions, classpath);

        Jar jar = analyzer.getJar();
        Manifest manifest = jar.getManifest();

        if (exportScr) {
            exportScr(analyzer, jar, scrLocation, buildContext, getLog());
        }

        // cleanup...
        analyzer.close();

        return manifest;
    }

    private static void exportScr(Analyzer analyzer, Jar jar, File scrLocation, BuildContext buildContext, Log log) throws Exception {
        log.debug("Export SCR metadata to: " + scrLocation.getPath());
        scrLocation.mkdirs();

        // export SCR metadata files from OSGI-INF/
        Map<String, Resource> scrDir = jar.getDirectories().get("OSGI-INF");
        if (scrDir != null) {
            for (Map.Entry<String, Resource> entry : scrDir.entrySet()) {
                String path = entry.getKey();
                Resource resource = entry.getValue();
                writeSCR(resource, new File(scrLocation, path), buildContext, log);
            }
        }

        // export metatype files from OSGI-INF/metatype
        Map<String, Resource> metatypeDir = jar.getDirectories().get(MetaTypeService.METATYPE_DOCUMENTS_LOCATION);
        if (metatypeDir != null) {
            for (Map.Entry<String, Resource> entry : metatypeDir.entrySet()) {
                String path = entry.getKey();
                Resource resource = entry.getValue();
                writeSCR(resource, new File(scrLocation, path), buildContext, log);
            }
        }

    }

    private static void writeSCR(Resource resource, File destination, BuildContext buildContext, Log log) throws Exception {
        log.debug("Write SCR file: " + destination.getPath());
        destination.getParentFile().mkdirs();
        OutputStream os = buildContext.newFileOutputStream(destination);
        try {
            resource.write(os);
        } finally {
            os.close();
        }
    }

    protected Analyzer getAnalyzer(MavenProject project, ClassPathItem[] classpath) throws IOException, MojoExecutionException, Exception {
        return getAnalyzer(project, new LinkedHashMap<>(), classpath);
    }

    protected Analyzer getAnalyzer(MavenProject project, Map<String, String> instructions, ClassPathItem[] classpath) throws IOException, MojoExecutionException, Exception {
        if (rebuildBundle && supportedProjectTypes.contains(project.getArtifact().getType())) {
            return buildOSGiBundle(project, instructions, classpath);
        }

        File file = getOutputDirectory();
        if (file == null) {
            file = project.getArtifact().getFile();
        }

        if (!file.exists()) {
            if (file.equals(getOutputDirectory())) {
                file.mkdirs();
            } else {
                throw new FileNotFoundException(file.getPath());
            }
        }

        Builder analyzer = getOSGiBuilder(project, instructions, classpath);

        analyzer.setJar(file);

        // calculateExportsFromContents when we have no explicit instructions
        // defining
        // the contents of the bundle *and* we are not analyzing the output
        // directory,
        // otherwise fall-back to addMavenInstructions approach

        boolean isOutputDirectory = file.equals(getOutputDirectory());

        if (analyzer.getProperty(Analyzer.EXPORT_PACKAGE) == null && analyzer.getProperty(Analyzer.EXPORT_CONTENTS) == null
            && analyzer.getProperty(Analyzer.PRIVATE_PACKAGE) == null && !isOutputDirectory) {
            String export = calculateExportsFromContents(analyzer.getJar());
            analyzer.setProperty(Analyzer.EXPORT_PACKAGE, export);
        }

        addMavenInstructions(project, analyzer);

        // if we spot Embed-Dependency and the bundle is "target/classes",
        // assume we need to rebuild
        if (analyzer.getProperty(DependencyEmbedder.EMBED_DEPENDENCY) != null && isOutputDirectory) {
            analyzer.build();
        } else {
            analyzer.mergeManifest(analyzer.getJar().getManifest());
            analyzer.getJar().setManifest(analyzer.calcManifest());
        }

        mergeMavenManifest(project, analyzer);

        boolean hasErrors = reportErrors("Manifest " + project.getArtifact(), analyzer);
        if (hasErrors) {
            String failok = analyzer.getProperty("-failok");
            if (null == failok || "false".equalsIgnoreCase(failok)) {
                throw new MojoFailureException("Error(s) found in manifest configuration");
            }
        }

        Jar jar = analyzer.getJar();

        if (unpackBundle) {
            File outputFile = getOutputDirectory();
            for (Entry<String, Resource> entry : jar.getResources().entrySet()) {
                File entryFile = new File(outputFile, entry.getKey());
                if (!entryFile.exists() || entry.getValue().lastModified() == 0) {
                    entryFile.getParentFile().mkdirs();
                    OutputStream os = buildContext.newFileOutputStream(entryFile);
                    entry.getValue().write(os);
                    os.close();
                }
            }
        }

        return analyzer;
    }

    private void writeIncrementalInfo(MavenProject project) throws MojoExecutionException {
        try {
            Path cacheData = getIncrementalDataPath(project);
            String curdata = getIncrementalData();
            Files.createDirectories(cacheData.getParent());
            try (Writer w = Files.newBufferedWriter(cacheData)) {
                w.append(curdata);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error checking manifest uptodate status", e);
        }
    }

    private boolean isUpToDate(MavenProject project) throws MojoExecutionException {
        try {
            Path cacheData = getIncrementalDataPath(project);
            String prvdata;
            if (Files.isRegularFile(cacheData)) {
                prvdata = new String(Files.readAllBytes(cacheData), StandardCharsets.UTF_8);
            } else {
                prvdata = null;
            }
            String curdata = getIncrementalData();
            if (curdata.equals(prvdata)) {
                long lastmod = Files.getLastModifiedTime(cacheData).toMillis();
                Set<String> stale = Stream.concat(Stream.of(new File(project.getBuild().getOutputDirectory())), project.getArtifacts().stream().map(Artifact::getFile))
                    .flatMap(f -> newer(lastmod, f)).collect(Collectors.toSet());
                if (!stale.isEmpty()) {
                    getLog().info("Stale files detected, re-generating manifest.");
                    if (showStaleFiles) {
                        getLog().info("Stale files: " + stale.stream().collect(Collectors.joining(", ")));
                    } else if (getLog().isDebugEnabled()) {
                        getLog().debug("Stale files: " + stale.stream().collect(Collectors.joining(", ")));
                    }
                } else {
                    // everything is in order, skip
                    getLog().info("Skipping manifest generation, everything is up to date.");
                    return true;
                }
            } else {
                if (prvdata == null) {
                    getLog().info("No previous run data found, generating manifest.");
                } else {
                    getLog().info("Configuration changed, re-generating manifest.");
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error checking manifest uptodate status", e);
        }
        return false;
    }

    private String getIncrementalData() {
        return getInstructions().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n", "", "\n"));
    }

    private Path getIncrementalDataPath(MavenProject project) {
        return Paths.get(project.getBuild().getDirectory(), "maven-bundle-plugin", "org.apache.felix_maven-bundle-plugin_manifest_xx");
    }

    private long lastmod(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private Stream<String> newer(long lastmod, File file) {
        try {
            if (file.isDirectory()) {
                return Files.walk(file.toPath()).filter(Files::isRegularFile).filter(p -> lastmod(p) > lastmod).map(Path::toString);
            } else if (file.isFile()) {
                if (lastmod(file.toPath()) > lastmod) {
                    if (file.getName().endsWith(".jar")) {
                        try (ZipFile zf = new ZipFile(file)) {
                            return zf.stream().filter(ze -> !ze.isDirectory()).filter(ze -> ze.getLastModifiedTime().toMillis() > lastmod)
                                .map(ze -> file.toString() + "!" + ze.getName()).collect(Collectors.toList()).stream();
                        }
                    } else {
                        return Stream.of(file.toString());
                    }
                } else {
                    return Stream.empty();
                }
            } else {
                return Stream.empty();
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    public static void writeManifest(Analyzer analyzer, File outputFile, boolean niceManifest, boolean exportScr, File scrLocation, BuildContext buildContext, Log log)
        throws Exception {
        Properties properties = analyzer.getProperties();
        Jar jar = analyzer.getJar();
        Manifest manifest = jar.getManifest();
        if (outputFile.exists() && properties.containsKey("Merge-Headers")) {
            Manifest analyzerManifest = manifest;
            manifest = new Manifest();
            InputStream inputStream = new FileInputStream(outputFile);
            try {
                manifest.read(inputStream);
            } finally {
                inputStream.close();
            }
            Instructions instructions = new Instructions(ExtList.from(analyzer.getProperty("Merge-Headers")));
            mergeManifest(instructions, manifest, analyzerManifest);
        } else {
            File parentFile = outputFile.getParentFile();
            parentFile.mkdirs();
        }
        writeManifest(manifest, outputFile, niceManifest, buildContext, log);

        if (exportScr) {
            exportScr(analyzer, jar, scrLocation, buildContext, log);
        }
    }

    public static void writeManifest(Manifest manifest, File outputFile, boolean niceManifest, BuildContext buildContext, Log log) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ManifestWriter.outputManifest(manifest, baos, niceManifest);
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                // nothing we can do here
            }
        }

        log.debug("Write manifest to " + outputFile.getPath());
        if (updateFile(outputFile.toPath(), baos.toByteArray())) {
            buildContext.refresh(outputFile);
        }
    }

    /**
     * Update a file with the given binary content if neeed.
     * The file won't be modified if the content is already the same.
     *
     * @param path the path of the file to update
     * @param newdata the new binary data, <code>null</code> to delete the file
     * @return <code>true</code> if the file was modified, <code>false</code> otherwise
     * @throws IOException if an exception occurs
     */
    public static boolean updateFile(Path path, byte[] newdata) throws IOException {
        if (newdata == null) {
            if (!Files.exists(path)) {
                return false;
            }
            Files.delete(path);
            return true;
        } else {
            byte[] olddata = new byte[0];
            if (Files.exists(path) && Files.isReadable(path)) {
                olddata = Files.readAllBytes(path);
            }
            if (Arrays.equals(olddata, newdata)) {
                return false;
            }
            Files.createDirectories(path.getParent());
            Files.write(path, newdata, StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        }
    }

    /*
     * Patched version of bnd's Analyzer.calculateExportsFromContents
     */
    public static String calculateExportsFromContents(Jar bundle) {
        String ddel = "";
        StringBuffer sb = new StringBuffer();
        Map<String, Map<String, Resource>> map = bundle.getDirectories();
        for (Iterator<Entry<String, Map<String, Resource>>> i = map.entrySet().iterator(); i.hasNext();) {
            // ----------------------------------------------------
            // should also ignore directories with no resources
            // ----------------------------------------------------
            Entry<String, Map<String, Resource>> entry = i.next();
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            // ----------------------------------------------------
            String directory = entry.getKey();
            if (directory.equals("META-INF") || directory.startsWith("META-INF/")) {
                continue;
            }
            if (directory.equals("OSGI-OPT") || directory.startsWith("OSGI-OPT/")) {
                continue;
            }
            if (directory.equals("/")) {
                continue;
            }

            if (directory.endsWith("/")) {
                directory = directory.substring(0, directory.length() - 1);
            }

            directory = directory.replace('/', '.');
            sb.append(ddel);
            sb.append(directory);
            ddel = ",";
        }
        return sb.toString();
    }
}
