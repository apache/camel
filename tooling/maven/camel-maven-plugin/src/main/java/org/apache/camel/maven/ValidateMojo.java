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
package org.apache.camel.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.SimpleValidationResult;
import org.apache.camel.catalog.lucene.LuceneSuggestionStrategy;
import org.apache.camel.catalog.maven.MavenVersionManager;
import org.apache.camel.maven.helper.EndpointHelper;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.XmlRouteParser;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.apache.camel.parser.model.CamelRouteDetails;
import org.apache.camel.parser.model.CamelSimpleExpressionDetails;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.exec.AbstractExecMojo;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.JavaClassSource;

/**
 * Parses the source code and validates the Camel routes has valid endpoint uris and simple expressions.
 *
 * @goal validate
 * @threadSafe
 */
public class ValidateMojo extends AbstractExecMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * Whether to fail if invalid Camel endpoints was found. By default the plugin logs the errors at WARN level
     *
     * @parameter property="camel.failOnError"
     *            default-value="false"
     */
    private boolean failOnError;

    /**
     * Whether to log endpoint URIs which was un-parsable and therefore not possible to validate
     *
     * @parameter property="camel.logUnparseable"
     *            default-value="false"
     */
    private boolean logUnparseable;

    /**
     * Whether to include Java files to be validated for invalid Camel endpoints
     *
     * @parameter property="camel.includeJava"
     *            default-value="true"
     */
    private boolean includeJava;

    /**
     * Whether to include XML files to be validated for invalid Camel endpoints
     *
     * @parameter property="camel.includeXml"
     *            default-value="true"
     */
    private boolean includeXml;

    /**
     * Whether to include test source code
     *
     * @parameter property="camel.includeTest"
     *            default-value="false"
     */
    private boolean includeTest;

    /**
     * To filter the names of java and xml files to only include files matching any of the given list of patterns (wildcard and regular expression).
     * Multiple values can be separated by comma.
     *
     * @parameter property="camel.includes"
     */
    private String includes;

    /**
     * To filter the names of java and xml files to exclude files matching any of the given list of patterns (wildcard and regular expression).
     * Multiple values can be separated by comma.
     *
     * @parameter property="camel.excludes"
     */
    private String excludes;

    /**
     * Whether to ignore unknown components
     *
     * @parameter property="camel.ignoreUnknownComponent"
     *            default-value="true"
     */
    private boolean ignoreUnknownComponent;

    /**
     * Whether to ignore incapable of parsing the endpoint uri
     *
     * @parameter property="camel.ignoreIncapable"
     *            default-value="true"
     */
    private boolean ignoreIncapable;

    /**
     * Whether to ignore components that uses lenient properties. When this is true, then the uri validation is stricter
     * but would fail on properties that are not part of the component but in the uri because of using lenient properties.
     * For example using the HTTP components to provide query parameters in the endpoint uri.
     *
     * @parameter property="camel.ignoreLenientProperties"
     *            default-value="true"
     */
    private boolean ignoreLenientProperties;

    /**
     * Whether to show all endpoints and simple expressions (both invalid and valid).
     *
     * @parameter property="camel.showAll"
     *            default-value="false"
     */
    private boolean showAll;

    /**
     * Whether to allow downloading Camel catalog version from the internet. This is needed if the project
     * uses a different Camel version than this plugin is using by default.
     *
     * @parameter property="camel.downloadVersion"
     *            default-value="true"
     */
    private boolean downloadVersion;

    /**
     * Whether to validate for duplicate route ids. Route ids should be unique and if there are duplicates
     * then Camel will fail to startup.
     *
     * @parameter property="camel.duplicateRouteId"
     *            default-value="true"
     */
    private boolean duplicateRouteId;

    // CHECKSTYLE:OFF
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        CamelCatalog catalog = new DefaultCamelCatalog();
        // add activemq as known component
        catalog.addComponent("activemq", "org.apache.activemq.camel.component.ActiveMQComponent");
        // enable did you mean
        catalog.setSuggestionStrategy(new LuceneSuggestionStrategy());
        // enable loading other catalog versions dynamically
        catalog.setVersionManager(new MavenVersionManager());
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

        List<CamelEndpointDetails> endpoints = new ArrayList<>();
        List<CamelSimpleExpressionDetails> simpleExpressions = new ArrayList<>();
        List<CamelRouteDetails> routeIds = new ArrayList<>();
        Set<File> javaFiles = new LinkedHashSet<File>();
        Set<File> xmlFiles = new LinkedHashSet<File>();

        // find all java route builder classes
        if (includeJava) {
            List list = project.getCompileSourceRoots();
            for (Object obj : list) {
                String dir = (String) obj;
                findJavaFiles(new File(dir), javaFiles);
            }
            if (includeTest) {
                list = project.getTestCompileSourceRoots();
                for (Object obj : list) {
                    String dir = (String) obj;
                    findJavaFiles(new File(dir), javaFiles);
                }
            }
        }
        // find all xml routes
        if (includeXml) {
            List list = project.getResources();
            for (Object obj : list) {
                Resource dir = (Resource) obj;
                findXmlFiles(new File(dir.getDirectory()), xmlFiles);
            }
            if (includeTest) {
                list = project.getTestResources();
                for (Object obj : list) {
                    Resource dir = (Resource) obj;
                    findXmlFiles(new File(dir.getDirectory()), xmlFiles);
                }
            }
        }

        for (File file : javaFiles) {
            if (matchFile(file)) {
                try {
                    List<CamelEndpointDetails> fileEndpoints = new ArrayList<>();
                    List<CamelRouteDetails> fileRouteIds = new ArrayList<>();
                    List<CamelSimpleExpressionDetails> fileSimpleExpressions = new ArrayList<>();
                    List<String> unparsable = new ArrayList<>();

                    // parse the java source code and find Camel RouteBuilder classes
                    String fqn = file.getPath();
                    String baseDir = ".";
                    JavaType out = Roaster.parse(file);
                    // we should only parse java classes (not interfaces and enums etc)
                    if (out instanceof JavaClassSource) {
                        JavaClassSource clazz = (JavaClassSource) out;
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
        }
        for (File file : xmlFiles) {
            if (matchFile(file)) {
                try {
                    List<CamelEndpointDetails> fileEndpoints = new ArrayList<>();
                    List<CamelSimpleExpressionDetails> fileSimpleExpressions = new ArrayList<>();
                    List<CamelRouteDetails> fileRouteIds = new ArrayList<>();

                    // parse the xml source code and find Camel routes
                    String fqn = file.getPath();
                    String baseDir = ".";

                    InputStream is = new FileInputStream(file);
                    XmlRouteParser.parseXmlRouteEndpoints(is, baseDir, fqn, fileEndpoints);
                    is.close();
                    // need a new stream
                    is = new FileInputStream(file);
                    XmlRouteParser.parseXmlRouteSimpleExpressions(is, baseDir, fqn, fileSimpleExpressions);
                    is.close();

                    if (duplicateRouteId) {
                        // need a new stream
                        is = new FileInputStream(file);
                        XmlRouteParser.parseXmlRouteRouteIds(is, baseDir, fqn, fileRouteIds);
                        is.close();
                    }

                    // add what we found in this file to the total list
                    endpoints.addAll(fileEndpoints);
                    simpleExpressions.addAll(fileSimpleExpressions);
                    routeIds.addAll(fileRouteIds);
                } catch (Exception e) {
                    getLog().warn("Error parsing xml file " + file + " code due " + e.getMessage(), e);
                }
            }
        }

        int endpointErrors = 0;
        int unknownComponents = 0;
        int incapableErrors = 0;
        for (CamelEndpointDetails detail : endpoints) {
            getLog().debug("Validating endpoint: " + detail.getEndpointUri());
            EndpointValidationResult result = catalog.validateEndpointProperties(detail.getEndpointUri(), ignoreLenientProperties);

            boolean ok = result.isSuccess();
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
            if (!ok) {
                if (result.getUnknownComponent() != null) {
                    unknownComponents++;
                } else if (result.getIncapable() != null) {
                    incapableErrors++;
                } else {
                    endpointErrors++;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Endpoint validation error at: ");
                if (detail.getClassName() != null && detail.getLineNumber() != null) {
                    // this is from java code
                    sb.append(detail.getClassName());
                    if (detail.getMethodName() != null) {
                        sb.append(".").append(detail.getMethodName());
                    }
                    sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                    sb.append(detail.getLineNumber()).append(")");
                } else if (detail.getLineNumber() != null) {
                    // this is from xml
                    String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                    if (fqn.endsWith(".xml")) {
                        fqn = fqn.substring(0, fqn.length() - 4);
                        fqn = asPackageName(fqn);
                    }
                    sb.append(fqn);
                    sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                    sb.append(detail.getLineNumber()).append(")");
                } else {
                    sb.append(detail.getFileName());
                }
                sb.append("\n\n");
                String out = result.summaryErrorMessage(false);
                sb.append(out);
                sb.append("\n\n");

                getLog().warn(sb.toString());
            } else if (showAll) {
                StringBuilder sb = new StringBuilder();
                sb.append("Endpoint validation passsed at: ");
                if (detail.getClassName() != null && detail.getLineNumber() != null) {
                    // this is from java code
                    sb.append(detail.getClassName());
                    if (detail.getMethodName() != null) {
                        sb.append(".").append(detail.getMethodName());
                    }
                    sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                    sb.append(detail.getLineNumber()).append(")");
                } else if (detail.getLineNumber() != null) {
                    // this is from xml
                    String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                    if (fqn.endsWith(".xml")) {
                        fqn = fqn.substring(0, fqn.length() - 4);
                        fqn = asPackageName(fqn);
                    }
                    sb.append(fqn);
                    sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                    sb.append(detail.getLineNumber()).append(")");
                } else {
                    sb.append(detail.getFileName());
                }
                sb.append("\n");
                sb.append("\n\t").append(result.getUri());
                sb.append("\n\n");

                getLog().info(sb.toString());
            }
        }
        String endpointSummary;
        if (endpointErrors == 0) {
            int ok = endpoints.size() - endpointErrors - incapableErrors - unknownComponents;
            endpointSummary = String.format("Endpoint validation success: (%s = passed, %s = invalid, %s = incapable, %s = unknown components)",
                    ok, endpointErrors, incapableErrors, unknownComponents);
        } else {
            int ok = endpoints.size() - endpointErrors - incapableErrors - unknownComponents;
            endpointSummary = String.format("Endpoint validation error: (%s = passed, %s = invalid, %s = incapable, %s = unknown components)",
                    ok, endpointErrors, incapableErrors, unknownComponents);
        }

        if (endpointErrors > 0) {
            getLog().warn(endpointSummary);
        } else {
            getLog().info(endpointSummary);
        }

        int simpleErrors = 0;
        for (CamelSimpleExpressionDetails detail : simpleExpressions) {
            SimpleValidationResult result;
            boolean predicate = detail.isPredicate();
            if (predicate) {
                getLog().debug("Validating simple predicate: " + detail.getSimple());
                result = catalog.validateSimplePredicate(detail.getSimple());
            } else {
                getLog().debug("Validating simple expression: " + detail.getSimple());
                result = catalog.validateSimpleExpression(detail.getSimple());
            }
            if (!result.isSuccess()) {
                simpleErrors++;

                StringBuilder sb = new StringBuilder();
                sb.append("Simple validation error at: ");
                if (detail.getClassName() != null && detail.getLineNumber() != null) {
                    // this is from java code
                    sb.append(detail.getClassName());
                    if (detail.getMethodName() != null) {
                        sb.append(".").append(detail.getMethodName());
                    }
                    sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                    sb.append(detail.getLineNumber()).append(")");
                } else if (detail.getLineNumber() != null) {
                    // this is from xml
                    String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                    if (fqn.endsWith(".xml")) {
                        fqn = fqn.substring(0, fqn.length() - 4);
                        fqn = asPackageName(fqn);
                    }
                    sb.append(fqn);
                    sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                    sb.append(detail.getLineNumber()).append(")");
                } else {
                    sb.append(detail.getFileName());
                }
                sb.append("\n");
                String[] lines = result.getError().split("\n");
                for (String line : lines) {
                    sb.append("\n\t").append(line);
                }
                sb.append("\n");

                getLog().warn(sb.toString());
            } else if (showAll) {
                StringBuilder sb = new StringBuilder();
                sb.append("Simple validation passed at: ");
                if (detail.getClassName() != null && detail.getLineNumber() != null) {
                    // this is from java code
                    sb.append(detail.getClassName());
                    if (detail.getMethodName() != null) {
                        sb.append(".").append(detail.getMethodName());
                    }
                    sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                    sb.append(detail.getLineNumber()).append(")");
                } else if (detail.getLineNumber() != null) {
                    // this is from xml
                    String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                    if (fqn.endsWith(".xml")) {
                        fqn = fqn.substring(0, fqn.length() - 4);
                        fqn = asPackageName(fqn);
                    }
                    sb.append(fqn);
                    sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                    sb.append(detail.getLineNumber()).append(")");
                } else {
                    sb.append(detail.getFileName());
                }
                sb.append("\n");
                sb.append("\n\t").append(result.getSimple());
                sb.append("\n\n");

                getLog().info(sb.toString());
            }
        }

        String simpleSummary;
        if (simpleErrors == 0) {
            int ok = simpleExpressions.size() - simpleErrors;
            simpleSummary = String.format("Simple validation success: (%s = passed, %s = invalid)", ok, simpleErrors);
        } else {
            int ok = simpleExpressions.size() - simpleErrors;
            simpleSummary = String.format("Simple validation error: (%s = passed, %s = invalid)", ok, simpleErrors);
        }

        if (simpleErrors > 0) {
            getLog().warn(simpleSummary);
        } else {
            getLog().info(simpleSummary);
        }

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

                    StringBuilder sb = new StringBuilder();
                    sb.append("Duplicate route id validation error at: ");
                    if (detail.getClassName() != null && detail.getLineNumber() != null) {
                        // this is from java code
                        sb.append(detail.getClassName());
                        if (detail.getMethodName() != null) {
                            sb.append(".").append(detail.getMethodName());
                        }
                        sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                        sb.append(detail.getLineNumber()).append(")");
                    } else if (detail.getLineNumber() != null) {
                        // this is from xml
                        String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                        if (fqn.endsWith(".xml")) {
                            fqn = fqn.substring(0, fqn.length() - 4);
                            fqn = asPackageName(fqn);
                        }
                        sb.append(fqn);
                        sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                        sb.append(detail.getLineNumber()).append(")");
                    } else {
                        sb.append(detail.getFileName());
                    }
                    sb.append("\n");
                    sb.append("\n\t").append(detail.getRouteId());
                    sb.append("\n\n");

                    getLog().warn(sb.toString());
                } else if (showAll) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Duplicate route id validation passed at: ");
                    if (detail.getClassName() != null && detail.getLineNumber() != null) {
                        // this is from java code
                        sb.append(detail.getClassName());
                        if (detail.getMethodName() != null) {
                            sb.append(".").append(detail.getMethodName());
                        }
                        sb.append("(").append(asSimpleClassName(detail.getClassName())).append(".java:");
                        sb.append(detail.getLineNumber()).append(")");
                    } else if (detail.getLineNumber() != null) {
                        // this is from xml
                        String fqn = stripRootPath(asRelativeFile(detail.getFileName()));
                        if (fqn.endsWith(".xml")) {
                            fqn = fqn.substring(0, fqn.length() - 4);
                            fqn = asPackageName(fqn);
                        }
                        sb.append(fqn);
                        sb.append("(").append(asSimpleClassName(fqn)).append(".xml:");
                        sb.append(detail.getLineNumber()).append(")");
                    } else {
                        sb.append(detail.getFileName());
                    }
                    sb.append("\n");
                    sb.append("\n\t").append(detail.getRouteId());
                    sb.append("\n\n");

                    getLog().info(sb.toString());
                }
            }
        }

        String routeIdSummary = "";
        if (duplicateRouteId) {
            if (duplicateRouteIdErrors == 0) {
                routeIdSummary = String.format("Duplicate route id validation success (%s = ids)", routeIds.size());
            } else {
                routeIdSummary = String.format("Duplicate route id validation error: (%s = ids, %s = duplicates)", routeIds.size(), duplicateRouteIdErrors);
            }

            if (duplicateRouteIdErrors > 0) {
                getLog().warn(routeIdSummary);
            } else {
                getLog().info(routeIdSummary);
            }
        }

        if (failOnError && (endpointErrors > 0 || simpleErrors > 0 || duplicateRouteIdErrors > 0)) {
            throw new MojoExecutionException(endpointSummary + "\n" + simpleSummary + "\n" + routeIdSummary);
        }
    }
    // CHECKSTYLE:ON

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

        List list = project.getDependencies();
        for (Object obj : list) {
            Dependency dep = (Dependency) obj;
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

    private void findJavaFiles(File dir, Set<File> javaFiles) {
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                } else if (file.isDirectory()) {
                    findJavaFiles(file, javaFiles);
                }
            }
        }
    }

    private void findXmlFiles(File dir, Set<File> xmlFiles) {
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".xml")) {
                    xmlFiles.add(file);
                } else if (file.isDirectory()) {
                    findXmlFiles(file, xmlFiles);
                }
            }
        }
    }

    private boolean matchFile(File file) {
        if (excludes == null && includes == null) {
            return true;
        }

        // exclude take precedence
        if (excludes != null) {
            for (String exclude : excludes.split(",")) {
                exclude = exclude.trim();
                // try both with and without directory in the name
                String fqn = stripRootPath(asRelativeFile(file.getAbsolutePath()));
                boolean match = EndpointHelper.matchPattern(fqn, exclude) || EndpointHelper.matchPattern(file.getName(), exclude);
                if (match) {
                    return false;
                }
            }
        }

        // include
        if (includes != null) {
            for (String include : includes.split(",")) {
                include = include.trim();
                // try both with and without directory in the name
                String fqn = stripRootPath(asRelativeFile(file.getAbsolutePath()));
                boolean match = EndpointHelper.matchPattern(fqn, include) || EndpointHelper.matchPattern(file.getName(), include);
                if (match) {
                    return true;
                }
            }
            // did not match any includes
            return false;
        }

        // was not excluded nor failed include so its accepted
        return true;
    }

    private String asRelativeFile(String name) {
        String answer = name;

        String base = project.getBasedir().getAbsolutePath();
        if (name.startsWith(base)) {
            answer = name.substring(base.length());
            // skip leading slash for relative path
            if (answer.startsWith(File.separator)) {
                answer = answer.substring(1);
            }
        }
        return answer;
    }

    private String stripRootPath(String name) {
        // strip out any leading source / resource directory

        List list = project.getCompileSourceRoots();
        for (Object obj : list) {
            String dir = (String) obj;
            dir = asRelativeFile(dir);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        list = project.getTestCompileSourceRoots();
        for (Object obj : list) {
            String dir = (String) obj;
            dir = asRelativeFile(dir);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        List resources = project.getResources();
        for (Object obj : resources) {
            Resource resource = (Resource) obj;
            String dir = asRelativeFile(resource.getDirectory());
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        resources = project.getTestResources();
        for (Object obj : resources) {
            Resource resource = (Resource) obj;
            String dir = asRelativeFile(resource.getDirectory());
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }

        return name;
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
