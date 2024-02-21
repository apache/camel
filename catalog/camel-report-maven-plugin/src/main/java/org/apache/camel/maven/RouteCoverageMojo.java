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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.camel.maven.htmlxlsx.process.CoverageResultsProcessor;
import org.apache.camel.maven.model.RouteCoverageNode;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.XmlRouteParser;
import org.apache.camel.parser.helper.RouteCoverageHelper;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.parser.model.CoverageData;
import org.apache.camel.util.FileUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.exec.AbstractExecMojo;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import static org.apache.camel.catalog.common.CatalogHelper.asRelativeFile;
import static org.apache.camel.catalog.common.CatalogHelper.findJavaRouteBuilderClasses;
import static org.apache.camel.catalog.common.CatalogHelper.findXmlRouters;
import static org.apache.camel.catalog.common.CatalogHelper.matchRouteFile;
import static org.apache.camel.catalog.common.CatalogHelper.stripRootPath;

/**
 * Performs route coverage reports after running Camel unit tests with camel-test modules
 */
@Mojo(name = "route-coverage", threadSafe = true)
public class RouteCoverageMojo extends AbstractExecMojo {

    public static final String DESTINATION_DIR = "/target/camel-route-coverage";
    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Whether to fail if a route was not fully covered.
     *
     * Note the option coverageThreshold can be used to set a minimum coverage threshold in percentage.
     *
     * @parameter property="camel.failOnError" default-value="false"
     */
    @Parameter(property = "camel.failOnError", defaultValue = "false")
    private boolean failOnError;

    /**
     * The minimum route coverage in percent when using failOnError.
     *
     * @parameter property="camel.coverageThreshold" default-value="100"
     */
    @Parameter(property = "camel.coverageThreshold", defaultValue = "100")
    private byte coverageThreshold = 100;

    /**
     * The minimum coverage across all routes in percent when using failOnError.
     *
     * @parameter property="camel.overallCoverageThreshold" default-value="0"
     */
    @Parameter(property = "camel.overallCoverageThreshold", defaultValue = "0")
    private byte overallCoverageThreshold;

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
     * Whether to allow anonymous routes (routes without any route id assigned). By using route id's then its safer to
     * match the route cover data with the route source code. Anonymous routes are less safe to use for route coverage
     * as its harder to know exactly which route that was tested corresponds to which of the routes from the source
     * code.
     */
    @Parameter(property = "camel.anonymousRoutes", defaultValue = "false")
    private boolean anonymousRoutes;

    /**
     * Whether to generate a coverage-report in Jacoco XML format.
     */
    @Parameter(property = "camel.generateJacocoXmlReport", defaultValue = "false")
    private boolean generateJacocoXmlReport;

    /**
     * Whether to generate a coverage-report in HTML format.
     */
    @Parameter(property = "camel.generateHtmlReport", defaultValue = "false")
    private boolean generateHtmlReport;

    @Override
    public void execute() throws MojoExecutionException {

        Set<File> javaFiles = new LinkedHashSet<>();
        Set<File> xmlFiles = new LinkedHashSet<>();

        // find all java route builder classes
        findJavaRouteBuilderClasses(javaFiles, true, includeTest, project);
        // find all xml routes
        findXmlRouters(xmlFiles, true, includeTest, project);

        List<CamelNodeDetails> routeTrees = new ArrayList<>();

        for (File file : javaFiles) {
            addJavaFiles(file, routeTrees);
        }
        for (File file : xmlFiles) {
            addXmlFiles(file, routeTrees);
        }

        getLog().info("Discovered " + routeTrees.size() + " routes");

        // skip any routes which has no route id assigned

        long anonymous = routeTrees.stream().filter(t -> t.getRouteId() == null).count();
        if (!anonymousRoutes && anonymous > 0) {
            getLog().warn(
                    "Discovered " + anonymous + " anonymous routes. Add route ids to these routes for route coverage support");
        }

        final AtomicInteger notCovered = new AtomicInteger();
        final AtomicInteger coveredNodes = new AtomicInteger();
        int totalNumberOfNodes = 0;

        List<CamelNodeDetails> routeIdTrees = routeTrees.stream().filter(t -> t.getRouteId() != null).toList();
        List<CamelNodeDetails> anonymousRouteTrees = routeTrees.stream().filter(t -> t.getRouteId() == null).toList();
        Document document = null;
        File file = null;
        Element report = null;

        if (generateJacocoXmlReport) {
            try {
                // creates the folder for the xml.file
                file = new File(project.getBasedir() + "/target/site/jacoco");
                if (!file.exists()) {
                    file.mkdirs();
                }
                document = createDocument();

                // report tag
                report = document.createElement("report");
                createAttrString(document, report, "name", "Camel Xml");
                document.appendChild(report);
            } catch (Exception e) {
                getLog().warn("Error generating Jacoco XML report due " + e.getMessage());
            }
        }

        // favor strict matching on route ids
        for (CamelNodeDetails t : routeIdTrees) {
            String routeId = t.getRouteId();
            String fileName = stripRootPath(asRelativeFile(t.getFileName(), project), project);
            String sourceFileName = new File(fileName).getName();
            String packageName = new File(fileName).getParent();
            Element pack = null;

            if (generateJacocoXmlReport && report != null) {
                // package tag
                pack = document.createElement("package");
                createAttrString(document, pack, "name", packageName);
                report.appendChild(pack);
            }

            // grab dump data for the route
            totalNumberOfNodes
                    = grabDumpData(t, routeId, totalNumberOfNodes, fileName, notCovered, coveredNodes, report, document,
                            sourceFileName, pack);
        }

        if (generateJacocoXmlReport && report != null) {
            doGenerateJacocoReport(file, document);
        }

        if (anonymousRoutes && !anonymousRouteTrees.isEmpty()) {
            totalNumberOfNodes = handleAnonymousRoutes(anonymousRouteTrees, totalNumberOfNodes, notCovered, coveredNodes);
        }

        if (generateHtmlReport) {
            doGenerateHtmlReport();
        }

        // compute and log overall coverage across routes
        AtomicBoolean overallCoverageAboveThreshold = new AtomicBoolean();
        String out = templateOverallCoverageData(coveredNodes.get(), totalNumberOfNodes, overallCoverageAboveThreshold);
        getLog().info("Overall coverage summary:\n\n" + out);
        getLog().info("");

        if (failOnError && notCovered.get() > 0) {
            throw new MojoExecutionException("There are " + notCovered.get() + " route(s) not fully covered!");
        } else if (failOnError && !overallCoverageAboveThreshold.get()) {
            throw new MojoExecutionException("The overall coverage is below " + overallCoverageThreshold + "%!");
        }
    }

    private int grabDumpData(
            CamelNodeDetails t, String routeId, int totalNumberOfNodes, String fileName, AtomicInteger notCovered,
            AtomicInteger coveredNodes, Element report, Document document, String sourceFileName, Element pack)
            throws MojoExecutionException {
        try {
            List<CoverageData> coverageData = RouteCoverageHelper
                    .parseDumpRouteCoverageByRouteId(project.getBasedir() + DESTINATION_DIR, routeId);
            if (coverageData.isEmpty()) {
                getLog().warn("No route coverage data found for route: " + routeId
                              + ". Make sure to enable route coverage in your unit tests and assign unique route ids to your routes. Also remember to run unit tests first.");
            } else {
                List<RouteCoverageNode> coverage = gatherRouteCoverageSummary(List.of(t), coverageData);
                totalNumberOfNodes += coverage.size();
                String out = templateCoverageData(fileName, routeId, coverage, notCovered, coveredNodes);
                getLog().info("Route coverage summary:\n\n" + out);
                getLog().info("");

                if (generateJacocoXmlReport && report != null) {
                    appendSourcefileNode(document, sourceFileName, pack, coverage);
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error during gathering route coverage data for route: " + routeId, e);
        }
        return totalNumberOfNodes;
    }

    private void doGenerateJacocoReport(File file, Document document) {
        try {
            getLog().info("Generating Jacoco XML report: " + file + "\n\n");
            createJacocoXmlFile(document, file);
        } catch (Exception e) {
            getLog().warn("Error generating Jacoco XML report due " + e.getMessage());
        }
    }

    private int handleAnonymousRoutes(
            List<CamelNodeDetails> anonymousRouteTrees, int totalNumberOfNodes, AtomicInteger notCovered,
            AtomicInteger coveredNodes)
            throws MojoExecutionException {
        // grab dump data for the route
        try {
            Map<String, List<CoverageData>> datas = RouteCoverageHelper
                    .parseDumpRouteCoverageByClassAndTestMethod(project.getBasedir() + DESTINATION_DIR);
            if (datas.isEmpty()) {
                getLog().warn("No route coverage data found"
                              + ". Make sure to enable route coverage in your unit tests. Also remember to run unit tests first.");
            } else {
                Map<String, List<CamelNodeDetails>> routes = groupAnonymousRoutesByClassName(anonymousRouteTrees);
                // attempt to match anonymous routes via the unit test class
                for (Map.Entry<String, List<CamelNodeDetails>> t : routes.entrySet()) {
                    List<RouteCoverageNode> coverage = new ArrayList<>();
                    String className = t.getKey();

                    // we may have multiple tests in the same test class that tests different parts of the same
                    // routes so merge their coverage reports into a single coverage
                    for (Map.Entry<String, List<CoverageData>> entry : datas.entrySet()) {
                        String key = entry.getKey();
                        String dataClassName = key.substring(0, key.indexOf('-'));
                        if (dataClassName.equals(className)) {
                            List<RouteCoverageNode> result = gatherRouteCoverageSummary(t.getValue(), entry.getValue());
                            // merge them together
                            mergeCoverageData(coverage, result);
                        }
                    }

                    if (!coverage.isEmpty()) {
                        totalNumberOfNodes += coverage.size();
                        String fileName
                                = stripRootPath(asRelativeFile(t.getValue().get(0).getFileName(), project), project);
                        String out = templateCoverageData(fileName, null, coverage, notCovered, coveredNodes);
                        getLog().info("Route coverage summary:\n\n" + out);
                        getLog().info("");
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error during gathering route coverage data ", e);
        }
        return totalNumberOfNodes;
    }

    private void doGenerateHtmlReport() {
        try {
            final String baseHtmlPath = "/target/site/route-coverage/html";
            final File htmlPath = new File(project.getBasedir() + baseHtmlPath);
            if (!htmlPath.exists()) {
                htmlPath.mkdirs();
            }
            final File cssPath = new File(project.getBasedir() + baseHtmlPath + "/static/css");
            if (!cssPath.exists()) {
                cssPath.mkdirs();
            }
            final File jsPath = new File(project.getBasedir() + baseHtmlPath + "/static/js");
            if (!jsPath.exists()) {
                jsPath.mkdirs();
            }
            getLog().info("");
            getLog().info("Generating HTML route coverage reports: " + htmlPath + "\n");
            CoverageResultsProcessor processor = new CoverageResultsProcessor();
            processor.writeCSS(cssPath);
            processor.writeJS(jsPath);
            File xmlPath = new File(project.getBasedir() + DESTINATION_DIR);
            String out = processor.generateReport(project, xmlPath, htmlPath);
            getLog().info(out);
        } catch (Exception e) {
            getLog().warn("Error generating HTML route coverage reports " + e.getMessage());
        }
    }

    private void addXmlFiles(File file, List<CamelNodeDetails> routeTrees) {
        if (matchFile(file)) {
            try {
                // parse the xml files code and find Camel routes
                String fqn = file.getPath();
                String baseDir = ".";
                InputStream is = new FileInputStream(file);
                List<CamelNodeDetails> result = XmlRouteParser.parseXmlRouteTree(is, baseDir, fqn);
                routeTrees.addAll(result);
                is.close();
            } catch (Exception e) {
                getLog().warn("Error parsing xml file " + file + " code due " + e.getMessage(), e);
            }
        }
    }

    private void addJavaFiles(File file, List<CamelNodeDetails> routeTrees) {
        if (matchFile(file)) {
            try {
                // parse the java source code and find Camel RouteBuilder classes
                String fqn = file.getPath();
                JavaType<?> out = Roaster.parse(file);
                // we should only parse java classes (not interfaces and enums etc)
                if (out instanceof JavaClassSource clazz) {
                    List<CamelNodeDetails> result = RouteBuilderParser.parseRouteBuilderTree(clazz, fqn, true);
                    routeTrees.addAll(result);
                }
            } catch (Exception e) {
                getLog().warn("Error parsing java file " + file + " code due " + e.getMessage(), e);
            }
        }
    }

    private Map<String, List<CamelNodeDetails>> groupAnonymousRoutesByClassName(List<CamelNodeDetails> anonymousRouteTrees) {
        Map<String, List<CamelNodeDetails>> answer = new LinkedHashMap<>();

        for (CamelNodeDetails t : anonymousRouteTrees) {
            String fileName = asRelativeFile(t.getFileName(), project);
            String className = FileUtil.stripExt(FileUtil.stripPath(fileName));
            List<CamelNodeDetails> list = answer.computeIfAbsent(className, k -> new ArrayList<>());
            list.add(t);
        }

        return answer;
    }

    private void mergeCoverageData(List<RouteCoverageNode> coverage, List<RouteCoverageNode> result) {
        List<RouteCoverageNode> toBeAdded = new ArrayList<>();

        ListIterator<RouteCoverageNode> it = null;
        for (RouteCoverageNode node : result) {
            // do we have an existing
            it = positionToLineNumber(it, coverage, node.getLineNumber());
            RouteCoverageNode existing = it.hasNext() ? it.next() : null;
            if (existing != null) {
                int count = existing.getCount() + node.getCount();
                existing.setCount(count);
            } else {
                // its a new node
                toBeAdded.add(node);
            }
        }

        if (!toBeAdded.isEmpty()) {
            coverage.addAll(toBeAdded);
        }
    }

    private ListIterator<RouteCoverageNode> positionToLineNumber(
            ListIterator<RouteCoverageNode> it, List<RouteCoverageNode> coverage, int lineNumber) {
        // restart
        if (it == null || !it.hasNext()) {
            it = coverage.listIterator();
        }
        while (it.hasNext()) {
            RouteCoverageNode node = it.next();
            if (node.getLineNumber() == lineNumber) {
                // go back
                it.previous();
                return it;
            }
        }
        return it;
    }

    @SuppressWarnings("unchecked")
    private String templateCoverageData(
            String fileName, String routeId, List<RouteCoverageNode> model, AtomicInteger notCovered,
            AtomicInteger coveredNodes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream sw = new PrintStream(bos);

        if (model.get(0).getClassName() != null) {
            sw.println("Class:\t" + model.get(0).getClassName());
        } else {
            sw.println("File:\t" + fileName);
        }
        if (routeId != null) {
            sw.println("Route:\t" + routeId);
        }
        sw.println();
        sw.printf("%8s    %8s    %s%n", "Line #", "Count", "Route");
        sw.printf("%8s    %8s    %s%n", "------", "-----", "-----");

        int covered = 0;
        for (RouteCoverageNode node : model) {
            if (node.getCount() > 0) {
                covered++;
            }
            String pad = padString(node.getLevel());
            sw.printf("%8s    %8s    %s%n", node.getLineNumber(), node.getCount(), pad + node.getName());
        }

        coveredNodes.addAndGet(covered);

        // calculate percentage of route coverage (must use double to have decimals)
        double percentage = ((double) covered / (double) model.size()) * 100;

        boolean success = true;
        if (covered != model.size() && percentage < coverageThreshold) {
            // okay here is a route that was not fully covered
            notCovered.incrementAndGet();
            success = false;
        }

        sw.println();
        sw.println("Coverage: " + covered + " out of " + model.size() + " (" + String.format(Locale.ROOT, "%.1f", percentage)
                   + "% / threshold " + coverageThreshold + ".0%)");
        sw.println("Status: " + (success ? "Success" : "Failed"));
        sw.println();

        return bos.toString();
    }

    private String templateOverallCoverageData(
            int coveredNodes, int totalNumberOfNodes, AtomicBoolean overallCoverageAboveThreshold) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream sw = new PrintStream(bos);

        // calculate percentage of overall coverage (must use double to have decimals)
        double percentage = totalNumberOfNodes > 0 ? ((double) coveredNodes / (double) totalNumberOfNodes) * 100 : 100;

        overallCoverageAboveThreshold.set(coveredNodes == totalNumberOfNodes || percentage >= overallCoverageThreshold);

        sw.println("Coverage: " + coveredNodes + " out of " + totalNumberOfNodes + " ("
                   + String.format(Locale.ROOT, "%.1f", percentage)
                   + "% / threshold " + overallCoverageThreshold + ".0%)");
        sw.println("Status: " + (overallCoverageAboveThreshold.get() ? "Success" : "Failed"));
        sw.println();

        return bos.toString();
    }

    private static List<RouteCoverageNode> gatherRouteCoverageSummary(
            List<CamelNodeDetails> route, List<CoverageData> coverageData) {
        List<RouteCoverageNode> answer = new ArrayList<>();

        Iterator<CoverageData> it = coverageData.iterator();
        for (CamelNodeDetails r : route) {
            AtomicInteger level = new AtomicInteger();
            gatherRouteCoverageSummary(r, it, level, answer);
        }

        return answer;
    }

    private static void gatherRouteCoverageSummary(
            CamelNodeDetails node, Iterator<CoverageData> it, AtomicInteger level, List<RouteCoverageNode> answer) {
        // we want to skip data for policy/transacted as they are abstract nodes and just gather their children immediately
        boolean skipData = "policy".equals(node.getName()) || "transacted".equals(node.getName());
        if (skipData) {
            for (CamelNodeDetails child : node.getOutputs()) {
                gatherRouteCoverageSummary(child, it, level, answer);
            }
            return;
        }

        // end block to make doTry .. doCatch .. doFinally aligned
        if ("doCatch".equals(node.getName()) || "doFinally".equals(node.getName())) {
            level.decrementAndGet();
        }

        RouteCoverageNode data = new RouteCoverageNode();
        data.setName(node.getName());
        data.setLineNumber(Integer.parseInt(node.getLineNumber()));
        data.setLevel(level.get());
        data.setClassName(node.getClassName());
        data.setMethodName(node.getMethodName());

        // add data
        answer.add(data);

        // find count
        boolean found = false;
        while (!found && it.hasNext()) {
            CoverageData holder = it.next();
            found = holder.getNode().equals(node.getName());
            if (found) {
                data.setCount(holder.getCount());
            }
        }

        if (node.getOutputs() != null) {
            level.addAndGet(1);
            for (CamelNodeDetails child : node.getOutputs()) {
                gatherRouteCoverageSummary(child, it, level, answer);
            }
            level.addAndGet(-1);
        }
    }

    private static String padString(int level) {
        return "  ".repeat(level);
    }

    private boolean matchFile(File file) {
        return matchRouteFile(file, excludes, includes, project);
    }

    private void appendSourcefileNode(
            Document document, String sourceFileName, Element pack,
            List<RouteCoverageNode> coverage) {
        Element sourcefile = document.createElement("sourcefile");
        createAttrString(document, sourcefile, "name", sourceFileName);
        pack.appendChild(sourcefile);

        int covered = 0;
        int missed = 0;
        for (RouteCoverageNode node : coverage) {
            int missedCount = 0;
            if (node.getCount() > 0) {
                covered++;
            } else {
                missedCount++;
                missed++;
            }
            // line tag
            Element line = document.createElement("line");
            createAttrInt(document, line, "nr", node.getLineNumber());
            createAttrInt(document, line, "mi", missedCount);
            createAttrInt(document, line, "ci", node.getCount());
            // provides no useful information, needed to be read by sonarQube
            createAttrInt(document, line, "mb", 0);
            createAttrInt(document, line, "cb", 0);
            sourcefile.appendChild(line);
        }

        // counter tag
        Element counter = document.createElement("counter");
        createAttrString(document, counter, "type", "LINE");
        createAttrInt(document, counter, "missed", missed);
        createAttrInt(document, counter, "covered", covered);
        sourcefile.appendChild(counter);
    }

    private static Attr createAttrInt(Document doc, Element e, String name, Integer value) {
        Attr a = doc.createAttribute(name);
        a.setValue(value.toString());
        e.setAttributeNode(a);

        return a;
    }

    private static Attr createAttrString(Document doc, Element e, String name, String value) {
        Attr a = doc.createAttribute(name);
        a.setValue(value);
        e.setAttributeNode(a);
        return a;
    }

    private static Document createDocument() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // turn off validator and loading external dtd
        dbf.setValidating(false);
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/namespaces", false);
        dbf.setFeature("http://xml.org/sax/features/validation", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
        return documentBuilder.newDocument();
    }

    private static void createJacocoXmlFile(Document document, File file) throws TransformerException {
        String xmlFilePath = file.toString() + "/xmlJacoco.xml";
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        DOMSource domSource = new DOMSource(document);
        StreamResult streamResult = new StreamResult(new File(xmlFilePath));

        transformer.transform(domSource, streamResult);
    }
}
