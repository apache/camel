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
package org.apache.camel.maven;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.catalog.common.FileUtil;
import org.apache.camel.catalog.lucene.LuceneSuggestionStrategy;
import org.apache.camel.catalog.maven.MavenVersionManager;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.XmlRouteParser;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.apache.camel.parser.model.CamelRouteDetails;
import org.apache.camel.parser.model.CamelSimpleExpressionDetails;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import static org.apache.camel.catalog.common.CatalogHelper.asRelativeFile;
import static org.apache.camel.catalog.common.CatalogHelper.findJavaRouteBuilderClasses;
import static org.apache.camel.catalog.common.CatalogHelper.findXmlRouters;
import static org.apache.camel.catalog.common.CatalogHelper.matchRouteFile;
import static org.apache.camel.catalog.common.CatalogHelper.stripRootPath;
import static org.apache.camel.catalog.common.FileUtil.findJavaFiles;

/**
 * Parses the source code and validates the Camel routes has valid endpoint uris and simple expressions, and validates
 * configuration files such as application.properties.
 */
@Mojo(name = "validate", threadSafe = true)
public class ValidateMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Skip the validation execution.
     */
    @Parameter(property = "camel.skipValidation", defaultValue = "false")
    private boolean skip;

    /**
     * Whether to fail if invalid Camel endpoints were found. By default, the plugin logs the errors at WARN level
     */
    @Parameter(property = "camel.failOnError", defaultValue = "false")
    private boolean failOnError;

    /**
     * Whether to log endpoint URIs which was un-parsable and therefore not possible to validate
     */
    @Parameter(property = "camel.logUnparseable", defaultValue = "false")
    private boolean logUnparseable;

    /**
     * Whether to include Java files to be validated for invalid Camel endpoints
     */
    @Parameter(property = "camel.includeJava", defaultValue = "true")
    private boolean includeJava;

    /**
     * List of extra maven repositories
     */
    @Parameter(property = "camel.extraRepositories")
    private String[] extraMavenRepositories;

    /**
     * List of sources transitive dependencies that contains camel routes
     */
    @Parameter(property = "camel.sourcesArtifacts")
    private String[] sourcesArtifacts;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    /**
     * Whether to include XML files to be validated for invalid Camel endpoints
     */
    @Parameter(property = "camel.includeXml", defaultValue = "true")
    private boolean includeXml;

    /**
     * Whether to include test source code
     */
    @Parameter(property = "camel.includeTest", defaultValue = "false")
    private boolean includeTest;

    /**
     * To filter the names of java and XML files to only include files matching any of the given list of patterns
     * (wildcard and regular expression). Multiple values can be separated by comma.
     */
    @Parameter(property = "camel.includes")
    private String includes;

    /**
     * To filter the names of java and XML files to exclude files matching any of the given pattern in the list
     * (wildcard and regular expression). Multiple values can be separated by comma.
     */
    @Parameter(property = "camel.excludes")
    private String excludes;

    /**
     * Whether to ignore unknown components
     */
    @Parameter(property = "camel.ignoreUnknownComponent", defaultValue = "true")
    private boolean ignoreUnknownComponent;

    /**
     * Whether to ignore incapable of parsing the endpoint uri
     */
    @Parameter(property = "camel.ignoreIncapable", defaultValue = "true")
    private boolean ignoreIncapable;

    /**
     * Whether to ignore deprecated options being used in the endpoint uri
     */
    @Parameter(property = "camel.ignoreDeprecated", defaultValue = "true")
    private boolean ignoreDeprecated;

    /**
     * Whether to ignore components that use lenient properties. When this is true, then the uri validation is stricter
     * but would fail on properties that are not part of the component but in the uri because of using lenient
     * properties. For example, using the HTTP components to provide query parameters in the endpoint uri.
     */
    @Parameter(property = "camel.ignoreLenientProperties", defaultValue = "true")
    private boolean ignoreLenientProperties;

    /**
     * Whether to show all endpoints and simple expressions (both invalid and valid).
     */
    @Parameter(property = "camel.showAll", defaultValue = "false")
    private boolean showAll;

    /**
     * Whether to allow downloading a Camel catalog version from the internet. This is needed if the project uses a
     * different Camel version than this plugin is used by default.
     */
    @Parameter(property = "camel.downloadVersion", defaultValue = "true")
    private boolean downloadVersion;

    /**
     * Whether to validate for duplicate route ids. Route ids should be unique, and if there are duplicates, then Camel
     * will fail to start up.
     */
    @Parameter(property = "camel.duplicateRouteId", defaultValue = "true")
    private boolean duplicateRouteId;

    /**
     * Whether to validate direct/seda/disruptor endpoints sending to non-existing consumers.
     */
    @Parameter(property = "camel.internalContextPairCheck", alias = "camel.directOrSedaPairCheck", defaultValue = "true")
    private boolean internalContextPairCheck;

    /**
     * When sourcesArtifacts are declared, choose to download transitive artifacts or not carefully enable this flag
     * since it will try to download the whole dependency tree
     */
    @Parameter(property = "camel.downloadTransitiveArtifacts", defaultValue = "false")
    private boolean downloadTransitiveArtifacts;

    /**
     * Location of configuration files to validate. The default is application.properties Multiple values can be
     * separated by comma and use wildcard pattern matching.
     */
    @Parameter(property = "camel.configurationFiles", defaultValue = "application.properties")
    private String configurationFiles;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    /**
     * javaFiles in memory cache, useful for multi modules maven project
     */
    private static final Set<File> javaFiles = new LinkedHashSet<>();
    /**
     * xmlFiles in memory cache, useful for multi modules maven projects
     */
    private static final Set<File> xmlFiles = new LinkedHashSet<>();

    private static final Set<String> downloadedArtifacts = new LinkedHashSet<>();

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("skipping route validation as per configuration");
            return;
        }

        downloadExtraSources();

        CamelCatalog catalog = new DefaultCamelCatalog();
        // add activemq as a known component
        catalog.addComponent("activemq", "org.apache.activemq.camel.component.ActiveMQComponent");
        // enable did you mean
        catalog.setSuggestionStrategy(new LuceneSuggestionStrategy());
        // enable loading other catalog versions dynamically
        catalog.setVersionManager(
                new MavenVersionManager(repositorySystem, repositorySystemSession, session.getSettings()));
        // use custom class loading
        catalog.getJSonSchemaResolver().setClassLoader(ValidateMojo.class.getClassLoader());
        // enable caching
        catalog.enableCache();

        String detectedVersion = findCamelVersion(project);
        if (detectedVersion != null) {
            getLog().info("Detected Camel version used in project: " + detectedVersion);
        }

        downloadCamelCatalogVersion(catalog);

        if (catalog.getLoadedVersion() != null) {
            getLog().info("Validating using downloaded Camel version: " + catalog.getLoadedVersion());
        } else {
            getLog().info("Validating using Camel version: " + catalog.getCatalogVersion());
        }

        doExecuteRoutes(catalog);
        doExecuteConfigurationFiles(catalog);
    }

    private void downloadCamelCatalogVersion(CamelCatalog catalog) {
        if (downloadVersion) {
            String catalogVersion = catalog.getCatalogVersion();
            String version = findCamelVersion(project);
            if (version != null && !version.equals(catalogVersion)) {
                // the project uses a different Camel version so attempt to load it
                getLog().info("Downloading Camel version: " + version);
                boolean loaded = catalog.loadVersion(version);
                if (!loaded) {
                    getLog().warn("Error downloading Camel version: " + version);
                }
            }
        }
    }

    /**
     * Download extra sources only if artifact sources are defined and the current project is not a parent project
     */
    private void downloadExtraSources() throws MojoExecutionException {
        if (!"pom".equals(project.getPackaging()) && sourcesArtifacts != null && sourcesArtifacts.length > 0) {
            // setup MavenDownloader, it will be used to download and locate artifacts declared via sourcesArtifacts

            List<String> artifacts = Arrays.asList(sourcesArtifacts);

            artifacts
                    .parallelStream()
                    .forEach(artifact -> {
                        if (!artifact.contains(":sources:")) {
                            getLog().warn("The artifact " + artifact
                                          + " does not contain sources classifier, and may be excluded in future releases");
                        }
                    });

            try (MavenDownloaderImpl downloader
                    = new MavenDownloaderImpl(repositorySystem, repositorySystemSession, session.getSettings())) {
                downloadArtifacts(downloader, artifacts);
            } catch (IOException e) {
                throw new MojoExecutionException(e);
            } catch (MavenResolutionException e) {
                // missing artifact, log and proceed
                getLog().warn(e.getMessage());
            }
        }
    }

    private void downloadArtifacts(MavenDownloaderImpl downloader, List<String> artifacts)
            throws MavenResolutionException, IOException {
        downloader.init();
        Set<String> repositorySet = Arrays.stream(extraMavenRepositories)
                .collect(Collectors.toSet());
        List<String> artifactList = new ArrayList<>(artifacts);

        // Remove already downloaded Artifacts
        artifactList.removeAll(downloadedArtifacts);

        if (!artifactList.isEmpty()) {
            doDownloadArtifacts(artifactList, downloader, repositorySet);
        }
    }

    private void doDownloadArtifacts(List<String> artifactList, MavenDownloaderImpl downloader, Set<String> repositorySet)
            throws MavenResolutionException, IOException {
        getLog().info("Downloading the following artifacts: " + artifactList);
        List<MavenArtifact> mavenSourcesArtifacts
                = downloader.resolveArtifacts(artifactList, repositorySet, downloadTransitiveArtifacts, false);

        // Create folder into the target folder that will be used to unzip
        // the downloaded artifacts
        Path extraSourcesPath = Paths.get(projectBuildDir, "camel-validate-sources");
        if (!Files.exists(extraSourcesPath)) {
            Files.createDirectories(extraSourcesPath);
        }

        // Unzip all the downloaded artifacts and add javas and xmls files into the cache
        unzipIntoCache(mavenSourcesArtifacts, extraSourcesPath);
    }

    private void unzipIntoCache(List<MavenArtifact> mavenSourcesArtifacts, Path extraSourcesPath) throws IOException {
        for (MavenArtifact artifact : mavenSourcesArtifacts) {
            final String gav = toGav(artifact);
            // Avoid downloading the same dependency multiple times
            downloadedArtifacts.add(gav);

            Path target = extraSourcesPath.resolve(artifact.getGav().getArtifactId());
            getLog().info("Unzipping the artifact: " + artifact + " to " + target);
            if (!Files.exists(target)) {
                unzipArtifact(artifact, target);
            }

            FileUtil.findJavaFiles(target.toFile(), javaFiles);
            FileUtil.findXmlFiles(target.toFile(), xmlFiles);
        }
    }

    private static String toGav(MavenArtifact artifact) {
        StringBuilder sb = new StringBuilder();
        sb.append(artifact.getGav().getGroupId()).append(":")
                .append(artifact.getGav().getArtifactId()).append(":")
                .append(artifact.getGav().getPackaging()).append(":")
                .append(artifact.getGav().getClassifier()).append(":")
                .append(artifact.getGav().getVersion());

        final String gav = sb.toString();
        return gav;
    }

    private static void unzipArtifact(MavenArtifact artifact, Path target) throws IOException {
        try (ZipFile zipFile = new ZipFile(artifact.getFile().toPath().toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            target = target.normalize();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path dest = target.resolve(entry.getName()).normalize();
                if (dest.startsWith(target)) {
                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        try (InputStream in = zipFile.getInputStream(entry)) {
                            Files.copy(in, dest);
                        }
                    }
                }
            }
        }
    }

    protected void doExecuteConfigurationFiles(CamelCatalog catalog) throws MojoExecutionException {
        Set<File> propertiesFiles = new LinkedHashSet<>();
        for (Resource dir : project.getResources()) {
            findPropertiesFiles(new File(dir.getDirectory()), propertiesFiles);
        }
        if (includeTest) {
            for (Resource dir : project.getTestResources()) {
                findPropertiesFiles(new File(dir.getDirectory()), propertiesFiles);
            }
        }

        List<ConfigurationPropertiesValidationResult> results = new ArrayList<>();

        for (File file : propertiesFiles) {
            parseProperties(catalog, file, results);
        }

        validateResults(results);
    }

    private void validateResults(List<ConfigurationPropertiesValidationResult> results) throws MojoExecutionException {
        ValidationComputedResult validationComputedResult = new ValidationComputedResult();

        for (ConfigurationPropertiesValidationResult result : results) {
            int deprecated = countDeprecated(result.getDeprecated());
            validationComputedResult.incrementDeprecatedOptionsBy(deprecated);

            boolean validationPassed = checkValidationPassed(validationComputedResult, result, deprecated);
            handleValidationResult(validationComputedResult, result, validationPassed);
        }
        String configurationSummary;
        if (validationComputedResult.getConfigurationErrors() == 0) {
            int ok = results.size() - validationComputedResult.getConfigurationErrors()
                     - validationComputedResult.getIncapableErrors() -
                     validationComputedResult.getUnknownComponents();
            configurationSummary = String.format(
                    "Configuration validation success: (%s = passed, %s = invalid, %s = incapable, %s = unknown components, %s = deprecated options)",
                    ok, validationComputedResult.getConfigurationErrors(), validationComputedResult.getIncapableErrors(),
                    validationComputedResult.getUnknownComponents(),
                    validationComputedResult.getDeprecatedOptions());
        } else {
            int ok = results.size() - validationComputedResult.getConfigurationErrors()
                     - validationComputedResult.getIncapableErrors() -
                     validationComputedResult.getUnknownComponents();
            configurationSummary = String.format(
                    "Configuration validation error: (%s = passed, %s = invalid, %s = incapable, %s = unknown components, %s = deprecated options)",
                    ok, validationComputedResult.getConfigurationErrors(), validationComputedResult.getIncapableErrors(),
                    validationComputedResult.getUnknownComponents(),
                    validationComputedResult.getDeprecatedOptions());
        }
        logErrorSummary(validationComputedResult.getConfigurationErrors(), configurationSummary);

        if (failOnError && (validationComputedResult.getConfigurationErrors() > 0)) {
            throw new MojoExecutionException(configurationSummary + "\n");
        }
    }

    private String buildValidationFailedSummary(ConfigurationPropertiesValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration validation error at: ");
        sb.append("(").append(result.getFileName());
        if (result.getLineNumber() > 0) {
            sb.append(":").append(result.getLineNumber());
        }
        sb.append(")");
        sb.append("\n\n");
        String out = result.summaryErrorMessage(false, ignoreDeprecated, true);
        sb.append(out);
        sb.append("\n\n");
        final String validationFailed = sb.toString();
        return validationFailed;
    }

    private static String buildValidationPassedSummary(ConfigurationPropertiesValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration validation passed at: ");
        sb.append(result.getFileName());
        if (result.getLineNumber() > 0) {
            sb.append(":").append(result.getLineNumber());
        }
        sb.append("\n");
        sb.append("\n\t").append(result.getText());
        sb.append("\n\n");

        final String validationPassed = sb.toString();
        return validationPassed;
    }

    private void parseProperties(CamelCatalog catalog, File file, List<ConfigurationPropertiesValidationResult> results) {
        if (matchPropertiesFile(file)) {
            try (InputStream is = new FileInputStream(file)) {
                Properties prop = new OrderedProperties();
                prop.load(is);

                // validate each line
                for (String name : prop.stringPropertyNames()) {
                    validateLine(catalog, file, results, name, prop);
                }
            } catch (Exception e) {
                getLog().warn("Error parsing file " + file + " code due " + e.getMessage(), e);
            }
        }
    }

    private void validateLine(
            CamelCatalog catalog, File file, List<ConfigurationPropertiesValidationResult> results, String name,
            Properties prop) {
        String value = prop.getProperty(name);
        if (value == null) {
            return;
        }

        final String text = name + "=" + value;
        ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(text);
        // only include lines that camel can accept (as there may be non camel properties too)
        if (result.isAccepted()) {
            // try to find line number
            int lineNumber = findLineNumberInPropertiesFile(file, name);
            if (lineNumber != -1) {
                result.setLineNumber(lineNumber);
            }
            results.add(result);
            result.setText(text);
            result.setFileName(file.getName());
        }
    }

    private int findLineNumberInPropertiesFile(File file, String name) {
        name = name.trim();
        // try to find the line number
        try (LineNumberReader r = new LineNumberReader(new FileReader(file))) {
            String line = r.readLine();
            while (line != null) {
                int pos = line.indexOf('=');
                if (pos > 0) {
                    line = line.substring(0, pos);
                }
                line = line.trim();
                if (line.equals(name)) {
                    return r.getLineNumber();
                }
                line = r.readLine();
            }
        } catch (Exception e) {
            // ignore
        }

        return -1;
    }

    protected void doExecuteRoutes(CamelCatalog catalog) throws MojoExecutionException {
        List<CamelEndpointDetails> endpoints = new ArrayList<>();
        List<CamelSimpleExpressionDetails> simpleExpressions = new ArrayList<>();
        List<CamelRouteDetails> routeIds = new ArrayList<>();

        // find all java route builder classes
        findJavaRouteBuilderClasses(javaFiles, includeJava, includeTest, project);
        // find all XML routes
        findXmlRouters(xmlFiles, includeXml, includeTest, project);

        for (File file : javaFiles) {
            if (matchFile(file)) {
                parseJavaRouteFile(endpoints, simpleExpressions, routeIds, file);
            }
        }
        for (File file : xmlFiles) {
            if (matchFile(file)) {
                parseXmlRouteFile(endpoints, simpleExpressions, routeIds, file);
            }
        }

        // endpoint uris
        validateResults(catalog, endpoints, simpleExpressions, routeIds);
    }

    private void validateResults(
            CamelCatalog catalog, List<CamelEndpointDetails> endpoints, List<CamelSimpleExpressionDetails> simpleExpressions,
            List<CamelRouteDetails> routeIds)
            throws MojoExecutionException {
        ValidationComputedResult validationComputedResult = new ValidationComputedResult();

        for (CamelEndpointDetails detail : endpoints) {
            getLog().debug("Validating endpoint: " + detail.getEndpointUri());
            EndpointValidationResult result
                    = catalog.validateEndpointProperties(detail.getEndpointUri(), ignoreLenientProperties);
            int deprecatedCount = countDeprecated(result.getDeprecated());
            validationComputedResult.incrementDeprecatedOptionsBy(deprecatedCount);

            boolean validationPassed = checkValidationPassed(validationComputedResult, result, deprecatedCount);
            handleValidationResult(validationComputedResult, detail, result, validationPassed);
        }
        String endpointSummary
                = buildEndpointSummaryMessage(endpoints, validationComputedResult.getEndpointErrors(),
                        validationComputedResult.getUnknownComponents(), validationComputedResult.getIncapableErrors(),
                        validationComputedResult.getDeprecatedOptions());
        logErrorSummary(validationComputedResult.getEndpointErrors(), endpointSummary);

        // simple
        int simpleErrors = validateSimple(catalog, simpleExpressions);
        String simpleSummary = buildSimpleSummaryMessage(simpleExpressions, simpleErrors);
        logErrorSummary(simpleErrors, simpleSummary);

        // endpoint pairs
        int endpointsWithError = 0;
        String logErrorSummary = "";
        if (internalContextPairCheck) {
            long numberOfEndpoints = (long) countEndpointPairs(endpoints, "direct")
                                     + (long) countEndpointPairs(endpoints, "seda")
                                     + (long) countEndpointPairs(endpoints, "disruptor")
                                     + (long) countEndpointPairs(endpoints, "disruptor-vm");
            endpointsWithError += validateEndpointPairs(endpoints, "direct")
                                  + validateEndpointPairs(endpoints, "seda")
                                  + validateEndpointPairs(endpoints, "disruptor")
                                  + validateEndpointPairs(endpoints, "disruptor-vm");
            logErrorSummary = getSedaDirectSummary(endpointsWithError, numberOfEndpoints);
            logErrorSummary(endpointsWithError, logErrorSummary);
        }

        // route id
        int duplicateRouteIdErrors = validateDuplicateRouteId(routeIds);
        String routeIdSummary = "";
        if (duplicateRouteId) {
            routeIdSummary = handleDuplicateRouteId(duplicateRouteIdErrors, routeIds);
        }

        if (failOnError && hasErrors(validationComputedResult.getEndpointErrors(), simpleErrors, duplicateRouteIdErrors)
                || endpointsWithError > 0) {
            throw new MojoExecutionException(
                    endpointSummary + "\n" + simpleSummary + "\n" + routeIdSummary + "\n" + logErrorSummary);
        }
    }

    private void handleValidationResult(
            ValidationComputedResult validationComputedResult, CamelEndpointDetails detail, EndpointValidationResult result,
            boolean validationPassed) {
        if (!validationPassed) {
            if (result.getUnknownComponent() != null) {
                validationComputedResult.incrementUnknownComponents();
            } else if (result.getIncapable() != null) {
                validationComputedResult.incrementIncapableErrors();
            } else {
                validationComputedResult.incrementEndpointErrors();
            }

            String msg = buildValidationErrorMessage(detail, result);

            getLog().warn(msg);
        } else if (showAll) {
            String msg = buildValidationPassedMessage(detail, result);

            getLog().info(msg);
        }
    }

    private void handleValidationResult(
            ValidationComputedResult validationComputedResult, ConfigurationPropertiesValidationResult result,
            boolean validationPassed) {
        if (!validationPassed) {
            if (result.getUnknownComponent() != null) {
                validationComputedResult.incrementUnknownComponents();
            } else if (result.getIncapable() != null) {
                validationComputedResult.incrementIncapableErrors();
            } else {
                validationComputedResult.incrementConfigurationErrors();
            }

            final String validationFailed = buildValidationFailedSummary(result);

            getLog().warn(validationFailed);
        } else if (showAll) {
            final String validationPassedSummary = buildValidationPassedSummary(result);

            getLog().info(validationPassedSummary);
        }
    }

    private boolean checkValidationPassed(
            ValidationComputedResult validationComputedResult, EndpointValidationResult result, int deprecatedCount) {
        boolean validationPassed = checkValidationPassed(result.isSuccess(), result.hasWarnings(), result.getUnknownComponent(),
                validationComputedResult, result.getIncapable(), deprecatedCount);
        return validationPassed;
    }

    private boolean checkValidationPassed(
            ValidationComputedResult validationComputedResult, ConfigurationPropertiesValidationResult result,
            int deprecatedCount) {
        boolean validationPassed = checkValidationPassed(result.isSuccess(), result.hasWarnings(), result.getUnknownComponent(),
                validationComputedResult, result.getIncapable(), deprecatedCount);
        return validationPassed;
    }

    private boolean checkValidationPassed(
            boolean success, boolean hasWarning, String unknownComponent, ValidationComputedResult validationComputedResult,
            String incapable, int deprecatedCount) {
        boolean validationPassed = success && !hasWarning;
        if (!validationPassed && ignoreUnknownComponent && unknownComponent != null) {
            // if we failed due unknown component, then be okay if we should ignore that
            validationComputedResult.incrementUnknownComponents();
            validationPassed = true;
        }
        if (!validationPassed && ignoreIncapable && incapable != null) {
            // if we failed due incapable then be okay if we should ignore that
            validationComputedResult.incrementIncapableErrors();
            validationPassed = true;
        }
        if (validationPassed && !ignoreDeprecated && deprecatedCount > 0) {
            validationPassed = false;
        }
        return validationPassed;
    }

    private static String getSedaDirectSummary(int endpointErrors, long totalPairs) {
        String summary;
        if (endpointErrors == 0) {
            summary = String.format("Endpoint pair (seda/direct/disruptor/disruptor-vm) validation success: (%s = pairs)",
                    totalPairs);
        } else {
            summary = String.format(
                    "Endpoint pair (seda/direct/disruptor/disruptor-vm) validation error: (%s = pairs, %s = non-pairs)",
                    totalPairs, endpointErrors);
        }
        return summary;
    }

    private static int countDeprecated(Set<String> result) {
        return result != null ? result.size() : 0;
    }

    private void logErrorSummary(int errors, String summary) {
        if (errors > 0) {
            getLog().warn(summary);
        } else {
            getLog().info(summary);
        }
    }

    private static boolean hasErrors(int endpointErrors, int simpleErrors, int duplicateRouteIdErrors) {
        return endpointErrors > 0 || simpleErrors > 0 || duplicateRouteIdErrors > 0;
    }

    private String handleDuplicateRouteId(int duplicateRouteIdErrors, List<CamelRouteDetails> routeIds) {
        String routeIdSummary;
        if (duplicateRouteIdErrors == 0) {
            routeIdSummary = String.format("Duplicate route id validation success: (%s = ids)", routeIds.size());
        } else {
            routeIdSummary = String.format("Duplicate route id validation error: (%s = ids, %s = duplicates)",
                    routeIds.size(), duplicateRouteIdErrors);
        }
        logErrorSummary(duplicateRouteIdErrors, routeIdSummary);
        return routeIdSummary;
    }

    private String buildSimpleSummaryMessage(List<CamelSimpleExpressionDetails> simpleExpressions, int simpleErrors) {
        String simpleSummary;
        if (simpleErrors == 0) {
            int ok = simpleExpressions.size() - simpleErrors;
            simpleSummary = String.format("Simple validation success: (%s = passed, %s = invalid)", ok, simpleErrors);
        } else {
            int ok = simpleExpressions.size() - simpleErrors;
            simpleSummary = String.format("Simple validation error: (%s = passed, %s = invalid)", ok, simpleErrors);
        }
        return simpleSummary;
    }

    private String buildEndpointSummaryMessage(
            List<CamelEndpointDetails> endpoints, int endpointErrors, int unknownComponents, int incapableErrors,
            int deprecatedOptions) {
        String endpointSummary;
        if (endpointErrors == 0) {
            int ok = endpoints.size() - endpointErrors - incapableErrors - unknownComponents;
            endpointSummary = String.format(
                    "Endpoint validation success: (%s = passed, %s = invalid, %s = incapable, %s = unknown components, %s = deprecated options)",
                    ok, endpointErrors, incapableErrors, unknownComponents, deprecatedOptions);
        } else {
            int ok = endpoints.size() - endpointErrors - incapableErrors - unknownComponents;
            endpointSummary = String.format(
                    "Endpoint validation error: (%s = passed, %s = invalid, %s = incapable, %s = unknown components, %s = deprecated options)",
                    ok, endpointErrors, incapableErrors, unknownComponents, deprecatedOptions);
        }
        return endpointSummary;
    }

    private String buildValidationPassedMessage(CamelEndpointDetails detail, EndpointValidationResult result) {
        StringBuilder sb = buildValidationSuccessMessage("Endpoint validation passed at: ", detail.getClassName(),
                detail.getLineNumber(), detail.getMethodName(), detail.getFileName(), result.getUri());

        return sb.toString();
    }

    private String buildValidationErrorMessage(CamelEndpointDetails detail, EndpointValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Endpoint validation error at: ");
        buildErrorMessage(sb, detail.getClassName(), detail.getLineNumber(), detail.getMethodName(), detail.getFileName());
        sb.append("\n\n");
        String out = result.summaryErrorMessage(false, ignoreDeprecated, true);
        sb.append(out);
        sb.append("\n\n");
        return sb.toString();
    }

    private void parseXmlRouteFile(
            List<CamelEndpointDetails> endpoints, List<CamelSimpleExpressionDetails> simpleExpressions,
            List<CamelRouteDetails> routeIds, File file) {
        try {
            List<CamelEndpointDetails> fileEndpoints = new ArrayList<>();
            List<CamelSimpleExpressionDetails> fileSimpleExpressions = new ArrayList<>();
            List<CamelRouteDetails> fileRouteIds = new ArrayList<>();

            // parse the XML source code and find Camel routes
            String fqn = file.getPath();
            String baseDir = ".";

            try (InputStream is = new FileInputStream(file)) {
                XmlRouteParser.parseXmlRouteEndpoints(is, baseDir, fqn, fileEndpoints);
            }
            // need a new stream
            try (InputStream is = new FileInputStream(file)) {
                XmlRouteParser.parseXmlRouteSimpleExpressions(is, baseDir, fqn, fileSimpleExpressions);
            }

            if (duplicateRouteId) {
                // need a new stream
                try (InputStream is = new FileInputStream(file)) {
                    XmlRouteParser.parseXmlRouteRouteIds(is, baseDir, fqn, fileRouteIds);
                }
            }

            // add what we found in this file to the total list
            endpoints.addAll(fileEndpoints);
            simpleExpressions.addAll(fileSimpleExpressions);
            routeIds.addAll(fileRouteIds);
        } catch (Exception e) {
            getLog().warn("Error parsing xml file " + file + " code due " + e.getMessage(), e);
        }
    }

    private void parseJavaRouteFile(
            List<CamelEndpointDetails> endpoints, List<CamelSimpleExpressionDetails> simpleExpressions,
            List<CamelRouteDetails> routeIds, File file) {
        try {
            List<CamelEndpointDetails> fileEndpoints = new ArrayList<>();
            List<CamelRouteDetails> fileRouteIds = new ArrayList<>();
            List<CamelSimpleExpressionDetails> fileSimpleExpressions = new ArrayList<>();
            List<String> unparsable = new ArrayList<>();

            // parse the java source code and find Camel RouteBuilder classes
            String fqn = file.getPath();
            String baseDir = ".";
            JavaType<?> out = Roaster.parse(file);
            // we should only parse java classes (not interfaces and enums etc.)
            if (out instanceof JavaClassSource clazz) {
                RouteBuilderParser.parseRouteBuilderEndpoints(clazz, baseDir, fqn, fileEndpoints, unparsable, includeTest);
                RouteBuilderParser.parseRouteBuilderSimpleExpressions(clazz, baseDir, fqn, fileSimpleExpressions);
                if (duplicateRouteId) {
                    RouteBuilderParser.parseRouteBuilderRouteIds(clazz, baseDir, fqn, fileRouteIds);
                }

                // add what we found in this file to the total list
                endpoints.addAll(fileEndpoints);
                simpleExpressions.addAll(fileSimpleExpressions);
                routeIds.addAll(fileRouteIds);

                // was there any unparsable?
                if (logUnparseable && !unparsable.isEmpty()) {
                    for (String uri : unparsable) {
                        getLog().warn("Cannot parse endpoint uri " + uri + " in java file " + file);
                    }
                }
            }
        } catch (Exception e) {
            getLog().warn("Error parsing java file " + file + " code due " + e.getMessage(), e);
        }
    }

    private int countEndpointPairs(List<CamelEndpointDetails> endpoints, String scheme) {
        int pairs = 0;

        Set<CamelEndpointDetails> consumers = endpoints.stream()
                .filter(e -> e.isConsumerOnly() && e.getEndpointUri().startsWith(scheme + ":")).collect(Collectors.toSet());
        Set<CamelEndpointDetails> producers = endpoints.stream()
                .filter(e -> e.isProducerOnly() && e.getEndpointUri().startsWith(scheme + ":")).collect(Collectors.toSet());

        // find all pairs, eg producers that has a consumer (no need to check for opposite)
        for (CamelEndpointDetails p : producers) {
            boolean any = consumers.stream().anyMatch(c -> matchEndpointPath(p.getEndpointUri(), c.getEndpointUri()));
            if (any) {
                pairs++;
            }
        }

        return pairs;
    }

    private int validateEndpointPairs(List<CamelEndpointDetails> endpoints, String scheme) {
        int errors = 0;

        Set<CamelEndpointDetails> consumers = endpoints.stream()
                .filter(e -> e.isConsumerOnly() && e.getEndpointUri().startsWith(scheme + ":")).collect(Collectors.toSet());
        Set<CamelEndpointDetails> producers = endpoints.stream()
                .filter(e -> e.isProducerOnly() && e.getEndpointUri().startsWith(scheme + ":")).collect(Collectors.toSet());

        // are there any producers that do not have a consumer pair?
        for (CamelEndpointDetails detail : producers) {
            boolean none = consumers.stream().noneMatch(c -> matchEndpointPath(detail.getEndpointUri(), c.getEndpointUri()));
            if (none) {
                errors++;

                final String msg = buildEndpointValidationErrorMessage(detail);
                getLog().warn(msg);
            } else if (showAll) {
                StringBuilder sb = buildValidationSuccessMessage("Endpoint pair (seda/direct) validation passed at: ",
                        detail.getClassName(), detail.getLineNumber(), detail.getMethodName(), detail.getFileName(),
                        detail.getEndpointUri());

                final String msg = sb.toString();
                getLog().info(msg);
            }
        }

        // NOTE: are there any consumers that do not have a producer pair
        // You can have a consumer which you send to from outside a Camel route such as via ProducerTemplate

        return errors;
    }

    private StringBuilder buildValidationSuccessMessage(
            String str, String className, String lineNumber, String methodName, String fileName, String uri) {
        StringBuilder sb = new StringBuilder();
        sb.append(str);
        buildErrorMessage(sb, className, lineNumber, methodName, fileName);
        sb.append("\n");
        sb.append("\n\t").append(uri);
        sb.append("\n\n");
        return sb;
    }

    private String buildEndpointValidationErrorMessage(CamelEndpointDetails detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("Endpoint pair (seda/direct) validation error at: ");
        buildErrorMessage(sb, detail.getClassName(), detail.getLineNumber(), detail.getMethodName(), detail.getFileName());
        sb.append("\n");
        sb.append("\n\t").append(detail.getEndpointUri());
        sb.append("\n\n\t\t\t\t").append(endpointPathSummaryError(detail));
        sb.append("\n\n");

        return sb.toString();
    }

    private void buildErrorMessage(StringBuilder sb, String className, String lineNumber, String methodName, String fileName) {
        if (className != null && lineNumber != null) {
            // this is from java code
            sb.append(className);
            if (methodName != null) {
                sb.append(".").append(methodName);
            }
            sb.append("(").append(asSimpleClassName(className)).append(".java:");
            sb.append(lineNumber).append(")");
        } else if (lineNumber != null) {
            // this is from xml
            String fqn = stripRootPath(asRelativeFile(fileName, project), project);
            if (fqn.endsWith(".xml")) {
                fqn = fqn.substring(0, fqn.length() - 4);
                fqn = asPackageName(fqn);
            }
            sb.append(fqn);
            sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
            sb.append(lineNumber).append(")");
        } else {
            sb.append(fileName);
        }
    }

    private static String endpointPathSummaryError(CamelEndpointDetails detail) {
        String uri = detail.getEndpointUri();
        String p = uri.contains("?") ? StringHelper.before(uri, "?") : uri;
        String path = StringHelper.after(p, ":");
        return path + "\t" + "Sending to non existing " + detail.getEndpointComponentName() + " queue name";
    }

    private static boolean matchEndpointPath(String uri, String uri2) {
        String p = uri.contains("?") ? StringHelper.before(uri, "?") : uri;
        String p2 = uri2.contains("?") ? StringHelper.before(uri2, "?") : uri2;
        p = p.trim();
        p2 = p2.trim();
        return p.equals(p2);
    }

    private int validateSimple(CamelCatalog catalog, List<CamelSimpleExpressionDetails> simpleExpressions) {
        int simpleErrors = 0;
        for (CamelSimpleExpressionDetails detail : simpleExpressions) {
            LanguageValidationResult result;
            boolean predicate = detail.isPredicate();
            if (predicate) {
                getLog().debug("Validating simple predicate: " + detail.getSimple());
                result = catalog.validateLanguagePredicate(null, "simple", detail.getSimple());
            } else {
                getLog().debug("Validating simple expression: " + detail.getSimple());
                result = catalog.validateLanguageExpression(null, "simple", detail.getSimple());
            }
            if (!result.isSuccess()) {
                simpleErrors++;

                StringBuilder sb = new StringBuilder();
                sb.append("Simple validation error at: ");
                buildErrorMessage(sb, detail.getClassName(), detail.getLineNumber(), detail.getMethodName(),
                        detail.getFileName());
                sb.append("\n");
                String[] lines = result.getError().split("\n");
                for (String line : lines) {
                    sb.append("\n\t").append(line);
                }
                sb.append("\n");

                getLog().warn(sb.toString());
            } else if (showAll) {
                StringBuilder sb = buildValidationSuccessMessage("Simple validation passed at: ", detail.getClassName(),
                        detail.getLineNumber(), detail.getMethodName(), detail.getFileName(), result.getText());

                getLog().info(sb.toString());
            }
        }
        return simpleErrors;
    }

    private int validateDuplicateRouteId(List<CamelRouteDetails> routeIds) {
        int duplicateRouteIdErrors = 0;
        if (duplicateRouteId) {
            // filter out all non uniques
            for (CamelRouteDetails detail : routeIds) {
                // skip empty route ids
                if (detail.getRouteId() == null || "".equals(detail.getRouteId())) {
                    continue;
                }
                int count = countRouteId(routeIds, detail.getRouteId());
                if (count > 1) {
                    duplicateRouteIdErrors++;

                    final String msg = buildRouteIdValidationMessage("Duplicate route id validation error at: ", detail);
                    getLog().warn(msg);
                } else if (showAll) {
                    final String msg = buildRouteIdValidationMessage("Duplicate route id validation passed at: ", detail);
                    getLog().info(msg);
                }
            }
        }
        return duplicateRouteIdErrors;
    }

    private String buildRouteIdValidationMessage(String str, CamelRouteDetails detail) {
        StringBuilder sb = buildValidationSuccessMessage(str, detail.getClassName(), detail.getLineNumber(),
                detail.getMethodName(), detail.getFileName(), detail.getRouteId());

        return sb.toString();
    }

    private static int countRouteId(List<CamelRouteDetails> details, String routeId) {
        int answer = 0;
        for (CamelRouteDetails detail : details) {
            if (routeId.equals(detail.getRouteId())) {
                answer++;
            }
        }
        return answer;
    }

    private static String findCamelVersion(MavenProject project) {
        Dependency candidate = null;

        for (Dependency dep : project.getDependencies()) {
            if ("org.apache.camel".equals(dep.getGroupId())) {
                if ("camel-core".equals(dep.getArtifactId())) {
                    // favor camel-core
                    candidate = dep;
                    break;
                } else {
                    candidate = dep;
                }
            }
        }
        if (candidate != null) {
            return candidate.getVersion();
        }

        return null;
    }

    private void findPropertiesFiles(File dir, Set<File> propertiesFiles) {
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".properties")) {
                    propertiesFiles.add(file);
                } else if (file.isDirectory()) {
                    findJavaFiles(file, propertiesFiles);
                }
            }
        }
    }

    private boolean matchPropertiesFile(File file) {
        for (String part : configurationFiles.split(",")) {
            part = part.trim();
            String fqn = stripRootPath(asRelativeFile(file.getAbsolutePath(), project), project);
            boolean match = PatternHelper.matchPattern(fqn, part);
            if (match) {
                return true;
            }
        }
        return false;
    }

    private boolean matchFile(File file) {
        return matchRouteFile(file, excludes, includes, project);
    }

    private static String asPackageName(String name) {
        return name.replace(File.separator, ".");
    }

    private static String asSimpleClassName(String className) {
        int dot = className.lastIndexOf('.');
        if (dot > 0) {
            return className.substring(dot + 1);
        } else {
            return className;
        }
    }

    private static class ValidationComputedResult {
        private int endpointErrors = 0;
        private int configurationErrors = 0;
        private int unknownComponents = 0;
        private int incapableErrors = 0;
        private int deprecatedOptions = 0;

        public void incrementEndpointErrors() {
            endpointErrors++;
        }

        public void incrementConfigurationErrors() {
            configurationErrors++;
        }

        public void incrementUnknownComponents() {
            unknownComponents++;
        }

        public void incrementIncapableErrors() {
            incapableErrors++;
        }

        public void incrementDeprecatedOptionsBy(int extra) {
            deprecatedOptions += extra;
        }

        public int getEndpointErrors() {
            return endpointErrors;
        }

        public int getConfigurationErrors() {
            return configurationErrors;
        }

        public int getUnknownComponents() {
            return unknownComponents;
        }

        public int getIncapableErrors() {
            return incapableErrors;
        }

        public int getDeprecatedOptions() {
            return deprecatedOptions;
        }
    }
}
