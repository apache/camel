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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.camel.maven.htmlxlsx.TestUtil;
import org.apache.camel.maven.htmlxlsx.model.Route;
import org.apache.camel.maven.htmlxlsx.model.RouteStatistic;
import org.apache.camel.maven.htmlxlsx.model.TestResult;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class CoverageResultsProcessorTest {

    private static final String TARGET = "_target";

    private static final String CAMEL_ROUTE_COVERAGE = "camel-route-coverage";

    private static final String SRC = "src";

    private static final String TEST = "test";

    private static final String RESOURCES = "resources";

    private static final String UNIT_TEST_ROUTE_XML
            = "com.example.route.ConditionalBeanRouteUnitTest-whenSendBodyWithFruit_thenFavouriteHeaderReceivedSuccessfully.xml";

    private static final String INDEX_HTML = "index.html";

    private static final String GREETINGS_ROUTE = "greetings-route";

    @Spy
    private CoverageResultsProcessor processor;

    @Spy
    private XmlToCamelRouteCoverageConverter converter;

    @TempDir
    private File temporaryDirectory;

    @Test
    public void testCoverageResultsProcessor() {

        // keep jacoco happy
        assertNotNull(processor);
    }

    @Test
    public void testGenerateReport() throws IOException {

        File htmlPath = htmlPath();
        Path htmlPathAsPath = Paths.get(htmlPath.getPath());
        if (!Files.exists(htmlPathAsPath)) {
            Files.createDirectories(htmlPathAsPath);
        }

        MavenProject mavenProject = new MavenProject();
        mavenProject.setName(RESOURCES);

        processor.generateReport(mavenProject, xmlPath(), htmlPath);

        assertTrue(Files.exists(Paths.get(indexPath().getPath())));
    }

    @Test
    public void testParseAllTestResults() throws IOException, IllegalAccessException {

        Mockito
                .doReturn(TestUtil.testResult())
                .when(processor).parseTestResult(any(String.class));

        processor.parseAllTestResults(xmlPath());

        @SuppressWarnings("unchecked")
        List<TestResult> result = (List<TestResult>) FieldUtils.readDeclaredField(processor, "testResults", true);

        assertAll(
                () -> assertNotNull(result),
                () -> assertFalse(result.isEmpty()),
                () -> assertEquals(4, result.size()),
                () -> assertNotNull(result.get(0).getTest()),
                () -> assertEquals("some_class", result.get(0).getTest().getClazz()),
                () -> assertEquals("some_method", result.get(0).getTest().getMethod()),
                () -> assertNotNull(result.get(0).getCamelContextRouteCoverage()),
                () -> assertFalse(result.get(0).getCamelContextRouteCoverage().getRoutes().getRouteList().isEmpty()));
    }

    @Test
    public void testParseTestResult() throws IOException {

        TestResult result = processor.parseTestResult(inputFile());

        assertAll(
                () -> assertNotNull(result),
                () -> assertNotNull(result.getTest()),
                () -> assertEquals("com.example.route.ConditionalBeanRouteUnitTest", result.getTest().getClazz()),
                () -> assertEquals("whenSendBodyWithFruit_thenFavouriteHeaderReceivedSuccessfully",
                        result.getTest().getMethod()),
                () -> assertNotNull(result.getCamelContextRouteCoverage()),
                () -> assertFalse(result.getCamelContextRouteCoverage().getRoutes().getRouteList().isEmpty()));
    }

    @Test
    public void testParseTestResultNull() throws IllegalAccessException {

        FieldUtils.writeDeclaredField(processor, "xmlToCamelRouteCoverageConverter", converter, true);

        Mockito
                .doReturn(null)
                .when(converter).convert(any(String.class));

        Assertions.assertThrows(AssertionError.class, () -> processor.parseTestResult(inputFile()));
    }

    @Test
    public void testGenerateEipStatistics() throws IllegalAccessException, IOException {

        Mockito
                .doReturn(outputPath().getPath())
                .when(processor).writeReportIndex(any(String.class), any(File.class));

        @SuppressWarnings("unchecked")
        Map<String, RouteStatistic> routeStatisticMap
                = (Map<String, RouteStatistic>) FieldUtils.readDeclaredField(processor, "routeStatisticMap", true);

        assertAll(
                () -> assertNotNull(routeStatisticMap),
                () -> assertTrue(routeStatisticMap.isEmpty()));

        processor.parseAllTestResults(xmlPath());
        processor.gatherBestRouteCoverages();
        processor.generateRouteStatistics("test project", outputPath());
        processor.generateEipStatistics();

        assertAll(
                () -> assertNotNull(routeStatisticMap),
                () -> assertFalse(routeStatisticMap.isEmpty()));

        RouteStatistic result = routeStatisticMap.get(GREETINGS_ROUTE);

        assertAll(
                () -> assertNotNull(result),
                () -> assertNotNull(result.getEipStatisticMap()),
                () -> assertEquals(3, result.getEipStatisticMap().size()));
    }

    @Test
    public void testGenerateChildEipStatistics() {

    }

    @Test
    public void testGenerateExcel() {

    }

    @Test
    public void testGenerateHtml() {

    }

    @Test
    public void testGatherBestRouteCoverages() throws IllegalAccessException, IOException {

        @SuppressWarnings("unchecked")
        List<TestResult> testResults = (List<TestResult>) FieldUtils.readDeclaredField(processor, "testResults", true);
        @SuppressWarnings("unchecked")
        Map<String, Route> result = (Map<String, Route>) FieldUtils.readDeclaredField(processor, "routeMap", true);

        processor.parseAllTestResults(xmlPath());
        testResults.add(TestUtil.testResult());

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(0, result.size()));

        processor.gatherBestRouteCoverages();
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(7, result.size()));
    }

    @Test
    public void testSquashDuplicateRoutes() throws IllegalAccessException {

        @SuppressWarnings("unchecked")
        Map<String, Route> result = (Map<String, Route>) FieldUtils.readDeclaredField(processor, "routeMap", true);

        result.clear();
        result.put("route1", TestUtil.route());
        result.put("route2", TestUtil.route());

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()));

        processor.squashDuplicateRoutes();

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.size()));
    }

    @Test
    public void testGenerateRouteStatistics() throws IllegalAccessException, IOException {

        Mockito
                .doReturn(indexPath().getPath())
                .when(processor).writeReportIndex(any(String.class), any(File.class));

        @SuppressWarnings("unchecked")
        Map<String, Route> routeMap = (Map<String, Route>) FieldUtils.readDeclaredField(processor, "routeMap", true);

        processor.parseAllTestResults(xmlPath());
        processor.gatherBestRouteCoverages();

        assertAll(
                () -> assertNotNull(routeMap),
                () -> assertEquals(7, routeMap.size()));

        String result = processor.generateRouteStatistics("test project", htmlPath());

        assertAll(
                () -> assertTrue(result.length() > 0),
                () -> assertEquals(indexPath().getPath(), result));
    }

    @Test
    public void testAddToRouteTotals() {

    }

    @Test
    public void testGetRouteStatistic() throws IllegalAccessException {

        @SuppressWarnings("unchecked")
        Map<String, RouteStatistic> routeStatisticMap
                = (Map<String, RouteStatistic>) FieldUtils.readDeclaredField(processor, "routeStatisticMap", true);

        assertAll(
                () -> assertNotNull(routeStatisticMap),
                () -> assertEquals(0, routeStatisticMap.size()));

        RouteStatistic result = processor.getRouteStatistic("test");

        RouteStatistic finalResult = result;
        assertAll(
                () -> assertNotNull(finalResult),
                () -> assertEquals("test", finalResult.getId()),
                () -> assertNotNull(routeStatisticMap),
                () -> assertEquals(1, routeStatisticMap.size()));

        result = processor.getRouteStatistic("test");
        RouteStatistic finalResult2 = result;

        assertAll(
                () -> assertNotNull(finalResult2),
                () -> assertEquals("test", finalResult2.getId()),
                () -> assertNotNull(routeStatisticMap),
                () -> assertEquals(1, routeStatisticMap.size()));
    }

    @Test
    public void testRecalculate() {

    }

    @Test
    public void testWriteDetailsAsHtml() throws IllegalAccessException, IOException {

        @SuppressWarnings("unchecked")
        Map<String, RouteStatistic> routeStatisticMap
                = (Map<String, RouteStatistic>) FieldUtils.readDeclaredField(processor, "routeStatisticMap", true);

        File outputPath = htmlPath();
        Path outputPathAsPath = Paths.get(outputPath.getPath());
        if (!Files.exists(outputPathAsPath)) {
            Files.createDirectories(outputPathAsPath);
        }

        processor.parseAllTestResults(xmlPath());
        processor.gatherBestRouteCoverages();
        processor.generateRouteStatistics("test project", outputPath);
        processor.generateEipStatistics();

        RouteStatistic routeStatistic = processor.getRouteStatistic(GREETINGS_ROUTE);

        assertAll(
                () -> assertNotNull(routeStatistic),
                () -> assertNotNull(routeStatistic.getEipStatisticMap()),
                () -> assertEquals(3, routeStatistic.getEipStatisticMap().size()));

        processor.writeDetailsAsHtml(routeStatistic, outputPath);
    }

    @Test
    public void testWriteReportIndex() throws IOException {

        File outputPath = htmlPath();
        Path outputPathAsPath = Paths.get(outputPath.getPath());
        if (!Files.exists(outputPathAsPath)) {
            Files.createDirectories(outputPathAsPath);
        }

        String result = processor.writeReportIndex("test-project", outputPath);

        assertAll(
                () -> assertTrue(result.length() > 0),
                () -> assertEquals(indexPath().getPath(), result));
    }

    private File xmlPath() {

        return Paths.get(SRC, TEST, RESOURCES, TARGET, CAMEL_ROUTE_COVERAGE).toFile();
    }

    private String inputFile() {

        return Paths.get(SRC, TEST, RESOURCES, TARGET, CAMEL_ROUTE_COVERAGE, UNIT_TEST_ROUTE_XML).toString();
    }

    private File outputPath() {

        return Paths.get(temporaryDirectory.getPath(), TARGET, CAMEL_ROUTE_COVERAGE, "html").toFile();
    }

    private File htmlPath() {

        return Paths.get(temporaryDirectory.getPath(), TARGET, CAMEL_ROUTE_COVERAGE, "html").toFile();
    }

    private File xlsxPath() {

        return Paths.get(temporaryDirectory.getPath(), TARGET, CAMEL_ROUTE_COVERAGE, "xlsx").toFile();
    }

    private File indexPath() {

        return Paths.get(temporaryDirectory.getPath(), TARGET, CAMEL_ROUTE_COVERAGE, "html", INDEX_HTML).toFile();
    }

}
