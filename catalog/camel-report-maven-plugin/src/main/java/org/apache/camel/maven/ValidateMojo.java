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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.catalog.lucene.LuceneSuggestionStrategy;
import org.apache.camel.catalog.maven.MavenVersionManager;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.XmlRouteParser;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.apache.camel.parser.model.CamelRouteDetails;
import org.apache.camel.parser.model.CamelSimpleExpressionDetails;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.exec.AbstractExecMojo;
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
public class ValidateMojo extends AbstractExecMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Whether to fail if invalid Camel endpoints was found. By default the plugin logs the errors at WARN level
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
     * To filter the names of java and xml files to only include files matching any of the given list of patterns
     * (wildcard and regular expression). Multiple values can be separated by comma.
     */
    @Parameter(property = "camel.includes")
    private String includes;

    /**
     * To filter the names of java and xml files to exclude files matching any of the given list of patterns (wildcard
     * and regular expression). Multiple values can be separated by comma.
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
     * Whether to ignore components that uses lenient properties. When this is true, then the uri validation is stricter
     * but would fail on properties that are not part of the component but in the uri because of using lenient
     * properties. For example using the HTTP components to provide query parameters in the endpoint uri.
     */
    @Parameter(property = "camel.ignoreLenientProperties", defaultValue = "true")
    private boolean ignoreLenientProperties;

    /**
     * Whether to show all endpoints and simple expressions (both invalid and valid).
     */
    @Parameter(property = "camel.showAll", defaultValue = "false")
    private boolean showAll;

    /**
     * Whether to allow downloading Camel catalog version from the internet. This is needed if the project uses a
     * different Camel version than this plugin is using by default.
     */
    @Parameter(property = "camel.downloadVersion", defaultValue = "true")
    private boolean downloadVersion;

    /**
     * Whether to validate for duplicate route ids. Route ids should be unique and if there are duplicates then Camel
     * will fail to startup.
     */
    @Parameter(property = "camel.duplicateRouteId", defaultValue = "true")
    private boolean duplicateRouteId;

    /**
     * Whether to validate direct/seda endpoints sending to non existing consumers.
     */
    @Parameter(property = "camel.directOrSedaPairCheck", defaultValue = "true")
    private boolean directOrSedaPairCheck;

    /**
     * Location of configuration files to validate. The default is application.properties Multiple values can be
     * separated by comma and use wildcard pattern matching.
     */
    @Parameter(property = "camel.configurationFiles")
    private String configurationFiles = "application.properties";

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    @Override
    public void execute() throws MojoExecutionException {
        CamelCatalog catalog = new DefaultCamelCatalog();
        // add activemq as known component
        catalog.addComponent("activemq", "org.apache.activemq.camel.component.ActiveMQComponent");
        // enable did you mean
        catalog.setSuggestionStrategy(new LuceneSuggestionStrategy());
        // enable loading other catalog versions dynamically
        catalog.setVersionManager(
                new MavenVersionManager(repositorySystem, repositorySystemSession, getSession().getSettings()));
        // use custom class loading
        catalog.getJSonSchemaResolver().setClassLoader(ValidateMojo.class.getClassLoader());
        // enable caching
        catalog.enableCache();

        String detectedVersion = findCamelVersion(project);
        if (detectedVersion != null) {
            getLog().info("Detected Camel version used in project: " + detectedVersion);
        }

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

        if (catalog.getLoadedVersion() != null) {
            getLog().info("Validating using downloaded Camel version: " + catalog.getLoadedVersion());
        } else {
            getLog().info("Validating using Camel version: " + catalog.getCatalogVersion());
        }

        doExecuteRoutes(catalog);
        doExecuteConfigurationFiles(catalog);
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
        int configurationErrors = 0;
        int unknownComponents = 0;
        int incapableErrors = 0;
        int deprecatedOptions = 0;
        for (ConfigurationPropertiesValidationResult result : results) {
            int deprecated = result.getDeprecated() != null ? result.getDeprecated().size() : 0;
            deprecatedOptions += deprecated;

            boolean ok = result.isSuccess() && !result.hasWarnings();
            if (!ok && ignoreUnknownComponent && result.getUnknownComponent() != null) {
                // if we failed due unknown component then be okay if we should ignore that
                unknownComponents++;
                ok = true;
            }
            if (!ok && ignoreIncapable && result.getIncapable() != null) {
                // if we failed due incapable then be okay if we should ignore that
                incapableErrors++;
                ok = true;
            }
            if (ok && !ignoreDeprecated && deprecated > 0) {
                ok = false;
            }

            if (!ok) {
                if (result.getUnknownComponent() != null) {
                    unknownComponents++;
                } else if (result.getIncapable() != null) {
                    incapableErrors++;
                } else {
                    configurationErrors++;
                }

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

                getLog().warn(sb.toString());
            } else if (showAll) {
                StringBuilder sb = new StringBuilder();
                sb.append("Configuration validation passed at: ");
                sb.append(result.getFileName());
                if (result.getLineNumber() > 0) {
                    sb.append(":").append(result.getLineNumber());
                }
                sb.append("\n");
                sb.append("\n\t").append(result.getText());
                sb.append("\n\n");

                getLog().info(sb.toString());
            }
        }
        String configurationSummary;
        if (configurationErrors == 0) {
            int ok = results.size() - configurationErrors - incapableErrors - unknownComponents;
            configurationSummary = String.format(
                    "Configuration validation success: (%s = passed, %s = invalid, %s = incapable, %s = unknown components, %s = deprecated options)",
                    ok, configurationErrors, incapableErrors, unknownComponents, deprecatedOptions);
        } else {
            int ok = results.size() - configurationErrors - incapableErrors - unknownComponents;
            configurationSummary = String.format(
                    "Configuration validation error: (%s = passed, %s = invalid, %s = incapable, %s = unknown components, %s = deprecated options)",
                    ok, configurationErrors, incapableErrors, unknownComponents, deprecatedOptions);
        }
        if (configurationErrors > 0) {
            getLog().warn(configurationSummary);
        } else {
            getLog().info(configurationSummary);
        }

        if (failOnError && (configurationErrors > 0)) {
            throw new MojoExecutionException(configurationSummary + "\n");
        }
    }

    private void parseProperties(CamelCatalog catalog, File file, List<ConfigurationPropertiesValidationResult> results) {
        if (matchPropertiesFile(file)) {
            try (InputStream is = new FileInputStream(file)) {
                Properties prop = new OrderedProperties();
                prop.load(is);

                // validate each line
                for (String name : prop.stringPropertyNames()) {
                    String value = prop.getProperty(name);
                    if (value != null) {
                        String text = name + "=" + value;
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
                }
            } catch (Exception e) {
                getLog().warn("Error parsing file " + file + " code due " + e.getMessage(), e);
            }
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
        Set<File> javaFiles = new LinkedHashSet<>();
        Set<File> xmlFiles = new LinkedHashSet<>();

        // find all java route builder classes
        findJavaRouteBuilderClasses(javaFiles, includeJava, includeTest, project);
        // find all xml routes
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
        int endpointErrors = 0;
        int unknownComponents = 0;
        int incapableErrors = 0;
        int deprecatedOptions = 0;
        for (CamelEndpointDetails detail : endpoints) {
            getLog().debug("Validating endpoint: " + detail.getEndpointUri());
            EndpointValidationResult result
                    = catalog.validateEndpointProperties(detail.getEndpointUri(), ignoreLenientProperties);
            int deprecated = result.getDeprecated() != null ? result.getDeprecated().size() : 0;
            deprecatedOptions += deprecated;

            boolean ok = result.isSuccess() && !result.hasWarnings();
            if (!ok && ignoreUnknownComponent && result.getUnknownComponent() != null) {
                // if we failed due unknown component then be okay if we should ignore that
                unknownComponents++;
                ok = true;
            }
            if (!ok && ignoreIncapable && result.getIncapable() != null) {
                // if we failed due incapable then be okay if we should ignore that
                incapableErrors++;
                ok = true;
            }
            if (ok && !ignoreDeprecated && deprecated > 0) {
                ok = false;
            }

            if (!ok) {
                if (result.getUnknownComponent() != null) {
                    unknownComponents++;
                } else if (result.getIncapable() != null) {
                    incapableErrors++;
                } else {
                    endpointErrors++;
                }

                String msg = buildValidationErrorMessage(detail, result);

                getLog().warn(msg);
            } else if (showAll) {
                String msg = buildValidationPassedMessage(detail, result);

                getLog().info(msg);
            }
        }
        String endpointSummary
                = buildEndpointSummaryMessage(endpoints, endpointErrors, unknownComponents, incapableErrors, deprecatedOptions);
        if (endpointErrors > 0) {
            getLog().warn(endpointSummary);
        } else {
            getLog().info(endpointSummary);
        }

        // simple
        int simpleErrors = validateSimple(catalog, simpleExpressions);
        String simpleSummary = buildSimpleSummaryMessage(simpleExpressions, simpleErrors);
        if (simpleErrors > 0) {
            getLog().warn(simpleSummary);
        } else {
            getLog().info(simpleSummary);
        }

        // endpoint pairs
        int sedaDirectErrors = 0;
        String sedaDirectSummary = "";
        if (directOrSedaPairCheck) {
            long sedaDirectEndpoints
                    = (long) countEndpointPairs(endpoints, "direct") + (long) countEndpointPairs(endpoints, "seda");
            sedaDirectErrors += validateEndpointPairs(endpoints, "direct") + validateEndpointPairs(endpoints, "seda");
            if (sedaDirectErrors == 0) {
                sedaDirectSummary
                        = String.format("Endpoint pair (seda/direct) validation success: (%s = pairs)", sedaDirectEndpoints);
            } else {
                sedaDirectSummary = String.format("Endpoint pair (seda/direct) validation error: (%s = pairs, %s = non-pairs)",
                        sedaDirectEndpoints, sedaDirectErrors);
            }
            if (sedaDirectErrors > 0) {
                getLog().warn(sedaDirectSummary);
            } else {
                getLog().info(sedaDirectSummary);
            }
        }

        // route id
        int duplicateRouteIdErrors = validateDuplicateRouteId(routeIds);
        String routeIdSummary = "";
        if (duplicateRouteId) {
            routeIdSummary = handleDuplicateRouteId(duplicateRouteIdErrors, routeIds);
        }

        if (failOnError && hasErrors(endpointErrors, simpleErrors, duplicateRouteIdErrors) || sedaDirectErrors > 0) {
            throw new MojoExecutionException(
                    endpointSummary + "\n" + simpleSummary + "\n" + routeIdSummary + "\n" + sedaDirectSummary);
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
        if (duplicateRouteIdErrors > 0) {
            getLog().warn(routeIdSummary);
        } else {
            getLog().info(routeIdSummary);
        }
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

            // parse the xml source code and find Camel routes
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
            // we should only parse java classes (not interfaces and enums etc)
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

        // are there any producers that do not have a consumer pair
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
}
