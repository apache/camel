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
package org.apache.camel.maven.htmlxlsx.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.maven.htmlxlsx.model.ChildEip;
import org.apache.camel.maven.htmlxlsx.model.ChildEipStatistic;
import org.apache.camel.maven.htmlxlsx.model.Components;
import org.apache.camel.maven.htmlxlsx.model.EipAttribute;
import org.apache.camel.maven.htmlxlsx.model.EipStatistic;
import org.apache.camel.maven.htmlxlsx.model.Route;
import org.apache.camel.maven.htmlxlsx.model.RouteStatistic;
import org.apache.camel.maven.htmlxlsx.model.RouteTotalsStatistic;
import org.apache.camel.maven.htmlxlsx.model.TestResult;
import org.apache.maven.project.MavenProject;

public class CoverageResultsProcessor {

    private static final String DETAILS_FILE = "/details.html";

    private static final String INDEX_FILE = "/index.html";

    private static final String REST = "rest";

    private static final String FROM = "from";

    private static final String URI = "uri";

    private final Map<String, Route> routeMap = new TreeMap<>();

    private final Map<String, RouteStatistic> routeStatisticMap = new TreeMap<>();

    private final List<TestResult> testResults = new ArrayList<>();

    private final RouteTotalsStatistic routeTotalsStatistic = new RouteTotalsStatistic();

    private final FileUtil fileUtil = new FileUtil();

    private final TestResultParser testResultParser = new TestResultParser();

    private final XmlToCamelRouteCoverageConverter xmlToCamelRouteCoverageConverter = new XmlToCamelRouteCoverageConverter();

    public String generateReport(MavenProject project, final File xmlPath, final File htmlPath) throws IOException {

        String out;

        parseAllTestResults(xmlPath);

        if (testResults.size() > 0) {

            gatherBestRouteCoverages();

            squashDuplicateRoutes();

            generateRouteStatistics(project.getName(), htmlPath);

            generateEipStatistics();

            generateHtml(htmlPath);

            out = String.format("Generated HTML reports for %d routes%n%n", routeStatisticMap.size());
        } else {
            out = "No routes found. No HTML reports were generated%n";
        }

        return out;
    }

    protected void parseAllTestResults(final File xmlPath) throws IOException {

        Set<String> testInputs = fileUtil.filesInDirectory(xmlPath);

        for (String inputFile : testInputs) {
            TestResult testResult = parseTestResult(inputFile);
            testResults.add(testResult);
        }
    }

    protected TestResult parseTestResult(final String inputFile) throws IOException {

        String fileAsString = fileUtil.readFile(inputFile);

        TestResult testResult = xmlToCamelRouteCoverageConverter.convert(fileAsString);

        assert testResult != null;

        return testResultParser.parse(testResult);
    }

    protected void generateEipStatistics() {

        // generate the statistics for each EIP within a route
        for (Route route : routeMap.values()) {

            Map<Integer, List<EipStatistic>> eipStatisticMap = new HashMap<>();

            Components components = route.getComponents();
            Map<String, List<EipAttribute>> eipAttributesMap = components.getAttributeMap();

            eipAttributesMap.forEach((key, eipAttributes) -> {

                // 'rest' is a route attribute, not an EIP, so it doesn't make sense to include it
                if (!key.equals(REST)) {

                    eipAttributes.forEach(eipAttribute -> {

                        EipStatistic eipStatistic = new EipStatistic();
                        eipStatistic.setId(key);
                        eipStatistic.setTested(eipAttribute.getExchangesTotal() > 0);
                        eipStatistic.setTotalProcessingTime(eipAttribute.getTotalProcessingTime());
                        eipStatistic.setProperties(eipAttribute.getProperties());

                        eipAttribute.getChildEipMap().forEach((childKey, childEipList) -> {

                            childEipList.forEach(childEip -> {
                                ChildEipStatistic childEipStatistic = new ChildEipStatistic();
                                childEipStatistic.setId(childEip.getId());
                                generateChildEipStatistics(childEip, childEipStatistic);
                                eipStatistic.getChildEipStatisticMap().put(childKey, childEipStatistic);
                            });
                        });

                        List<EipStatistic> eipStatisticList;
                        if (eipStatisticMap.containsKey(eipAttribute.getIndex())) {
                            eipStatisticList = eipStatisticMap.get(eipAttribute.getIndex());
                        } else {
                            eipStatisticList = new ArrayList<>();
                        }
                        eipStatisticList.add(eipStatistic);
                        eipStatisticMap.put(eipAttribute.getIndex(), eipStatisticList);
                    });
                }
            });

            RouteStatistic routeStatistic = routeStatisticMap.get(route.getId());
            routeStatistic.setEipStatisticMap(eipStatisticMap);
            routeStatisticMap.put(route.getId(), routeStatistic);
        }
    }

    protected void generateChildEipStatistics(ChildEip childEip, ChildEipStatistic childEipStatistic) {

        childEip.getEipAttributeMap().forEach((key, value) -> {

            if (value instanceof EipAttribute) {

                EipAttribute eipAttribute = (EipAttribute) value;

                EipStatistic eipStatistic = new EipStatistic();
                eipStatistic.setId(key);
                eipStatistic.setTested(eipAttribute.getExchangesTotal() > 0);
                eipStatistic.setTotalProcessingTime(eipAttribute.getTotalProcessingTime());
                eipStatistic.setProperties(eipAttribute.getProperties());

                childEipStatistic.getEipStatisticMap().put(eipAttribute.getIndex(), eipStatistic);
            } else if (value instanceof String) {

                Properties properties = new Properties();
                properties.put("value", value);
                EipStatistic eipStatistic = new EipStatistic();
                eipStatistic.setId(key);
                eipStatistic.setProperties(properties);

                childEipStatistic.getEipStatisticMap().put(0, eipStatistic);
            }
        });
    }

    protected void generateHtml(final File outputPath) throws IOException {

        for (RouteStatistic routeStatistic : routeStatisticMap.values()) {
            writeDetailsAsHtml(routeStatistic, outputPath);
        }
    }

    protected void gatherBestRouteCoverages() {

        // get a de-duplicated list of the routes
        testResults.forEach(testResult -> {

            List<Route> routeList = testResult.getCamelContextRouteCoverage().getRoutes().getRouteList();
            routeList.forEach(route -> {

                String routeId = route.getId();

                Route mappedRoute = routeMap.get(routeId);
                if (mappedRoute == null) {
                    // if the route only appears once, this will handle it
                    routeMap.put(routeId, route);
                    mappedRoute = routeMap.get(routeId);
                }

                // if the route appears multiple times in the test results,
                // look for the route with the best coverage of EIPs
                try {
                    if (route.getExchangesTotal() > mappedRoute.getExchangesTotal()) {
                        routeMap.put(routeId, route);
                    }
                } catch (Exception t) {
                    // this is an edge case that needs to be identified. Log some useful debugging information.
                    System.out.println(t.getClass().toString());
                    System.out.printf("routeID: %s%n", routeId);
                    System.out.printf("route: %s%n", route);
                    System.out.printf("mappedRoute: %s%n", mappedRoute != null ? mappedRoute.toString() : "null");
                }
            });
        });
    }

    protected void squashDuplicateRoutes() {

        Map<String, String> squashMap = new TreeMap<>();

        // create a map using the 'from' URI as the key to eliminate duplicates
        routeMap.forEach((key, route) -> {
            Map<String, Object> from = (Map<String, Object>) route.getComponentsMap().get(FROM);
            String uri = from.get(URI).toString();
            squashMap.put(uri, key);
        });

        // use the de-duplicated URIs to create a route map with unique routes, regardless of the ID
        Map<String, Route> squashedRouteMap = new TreeMap<>();
        squashMap.forEach((key, value) -> squashedRouteMap.put(value, routeMap.get(value)));

        routeMap.clear();
        routeMap.putAll(squashedRouteMap);
    }

    protected String generateRouteStatistics(final String project, final File outputPath) throws IOException {

        // generate the statistics for each route
        routeMap.values().forEach(route -> {

            String routeId = route.getId();
            RouteStatistic routeStatistic = getRouteStatistic(routeId);

            routeStatistic = recalculate(route, routeStatistic);

            routeStatisticMap.put(routeId, routeStatistic);

            addToRouteTotals(routeStatistic);
        });

        return writeReportIndex(project, outputPath);
    }

    protected void addToRouteTotals(RouteStatistic routeStatistic) {

        routeTotalsStatistic.incrementTotalEips(routeStatistic.getTotalEips());
        routeTotalsStatistic.incrementTotalEipsTested(routeStatistic.getTotalEipsTested());
        routeTotalsStatistic.incrementTotalProcessingTime(routeStatistic.getTotalProcessingTime());
    }

    protected RouteStatistic getRouteStatistic(String routeId) {

        RouteStatistic routeStatistic;
        if (!routeStatisticMap.containsKey(routeId)) {
            routeStatistic = new RouteStatistic();
            routeStatistic.setId(routeId);
            routeStatisticMap.put(routeId, routeStatistic);
        } else {
            routeStatistic = routeStatisticMap.get(routeId);
        }

        return routeStatistic;
    }

    protected RouteStatistic recalculate(final Route route, final RouteStatistic routeStatistic) {

        AtomicInteger totalEips = new AtomicInteger(routeStatistic.getTotalEips());
        AtomicInteger totalEipsTested = new AtomicInteger(routeStatistic.getTotalEipsTested());
        AtomicInteger totalProcessingTime = new AtomicInteger(routeStatistic.getTotalProcessingTime());
        route.getComponents().getAttributeMap().values().forEach(eipAttributes -> {
            if (!routeStatistic.isTotalEipsInitialized()) {
                // prevent adding the route multiple times
                totalEips.getAndAdd(eipAttributes.size());
            }

            eipAttributes.forEach(eipAttribute -> {
                totalEipsTested.getAndAdd(eipAttribute.getExchangesTotal());
                totalProcessingTime.getAndAdd(eipAttribute.getTotalProcessingTime());
            });
        });

        // this is a hack because of some weird calculation bug that I cannot find
        if (totalEipsTested.get() > totalEips.get()) {
            totalEipsTested.set(totalEips.get());
        }
        int coverage = 0;
        if (totalEips.get() > 0) {
            coverage = (100 * totalEipsTested.get()) / totalEips.get();
        }

        RouteStatistic retval = new RouteStatistic();

        retval.setId(route.getId());
        retval.setTotalEips(totalEips.get());
        retval.setTotalEipsTested(totalEipsTested.get());
        retval.setTotalProcessingTime(totalProcessingTime.get());
        retval.setCoverage(coverage);
        retval.setTotalEipsInitialized(true);

        return retval;
    }

    protected void writeDetailsAsHtml(final RouteStatistic routeStatistic, final File outputPath) throws IOException {

        Map<String, Object> data = new HashMap<>();
        data.put("route", routeStatistic);
        data.put("eips", routeStatistic.getEipStatisticMap().entrySet());

        String rendered = TemplateRenderer.render(DETAILS_FILE, data);
        fileUtil.write(rendered, routeStatistic.getId(), outputPath);
    }

    protected String writeReportIndex(final String project, final File outputPath) throws IOException {

        Map<String, Object> data = new HashMap<>();
        data.put("project", project);
        data.put("routes", routeStatisticMap.values());
        data.put("totals", routeTotalsStatistic);

        String rendered = TemplateRenderer.render(INDEX_FILE, data);

        return fileUtil.write(rendered, "index", outputPath);
    }

    public void writeCSS(File cssPath) throws IOException {

        writeStaticFile("static/css/", "datatables.min.css", cssPath);
    }

    public void writeJS(File jsPath) throws IOException {

        writeStaticFile("static/js/", "datatables.min.js", jsPath);
    }

    protected void writeStaticFile(String baseInputPath, String filename, File baseOutputPath) throws IOException {

        String inputPath = Path.of(baseInputPath, filename).toString();
        String css = fileUtil.readFileFromClassPath(inputPath);
        Path outputPath = Paths.get(baseOutputPath.getPath(), filename);
        fileUtil.write(css, outputPath);
    }
}
