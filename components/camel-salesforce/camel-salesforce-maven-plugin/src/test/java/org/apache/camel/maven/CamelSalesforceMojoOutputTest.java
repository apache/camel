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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.test.junit4.TestSupport;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CamelSalesforceMojoOutputTest {
    private static final String FIXED_DATE = "Thu Mar 09 16:15:49 ART 2017";
    private static final String TEST_CALCULATED_FORMULA_FILE = "complex_calculated_formula.json";
    private static final String TEST_CASE_FILE = "case.json";

    @Parameter(1)
    public SObjectDescription description;

    @Parameter(3)
    public String expected;

    @Parameter(0)
    public String json;

    @Parameter(4)
    public GenerateMojo mojo;

    @Parameter(2)
    public String source;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testProcessDescription() throws Exception {
        final File pkgDir = temp.newFolder();

        final GenerateMojo.GeneratorUtility utility = mojo.new GeneratorUtility();

        mojo.processDescription(pkgDir, description, utility, FIXED_DATE);

        final File generatedFile = new File(pkgDir, source);
        final String generatedContent = FileUtils.readFileToString(generatedFile, StandardCharsets.UTF_8);

        if (TestSupport.getJavaMajorVersion() >= 9
            && (source.equals("Case.java") || source.equals("ComplexCalculatedFormula.java"))) {
            // Content is the same, the ordering is a bit different.
            source += "-Java9";
        }
        final String expectedContent = IOUtils.toString(
            CamelSalesforceMojoOutputTest.class.getResource("/generated/" + expected), StandardCharsets.UTF_8);

        Assert.assertEquals(
            "Generated source file in " + source + " must be equal to the one present in test/resources",
            generatedContent, expectedContent);
    }

    @Parameters(name = "json = {0}, source = {2}")
    public static Iterable<Object[]> parameters() throws IOException {
        return Arrays.asList(testCase(TEST_CASE_FILE, "Case.java"),
            testCase(TEST_CASE_FILE, "Case_PickListAccentMarkEnum.java"),
            testCase(TEST_CASE_FILE, "Case_PickListQuotationMarkEnum.java"),
            testCase(TEST_CASE_FILE, "Case_PickListSlashEnum.java"), //
            testCase(TEST_CASE_FILE, "QueryRecordsCase.java"),
            testCase(TEST_CALCULATED_FORMULA_FILE, "ComplexCalculatedFormula.java"),
            testCase(TEST_CALCULATED_FORMULA_FILE, "QueryRecordsComplexCalculatedFormula.java"),
            testCase("asset.json", "Asset.java"), //
            testCase("asset.json", "Asset.java", "Asset_LocalDateTime.java", mojo -> {
                mojo.customTypes = new HashMap<>();
                mojo.customTypes.put("date", "java.time.LocalDateTime");

                mojo.setup();
            }));
    }

    static GenerateMojo createMojo() {
        final GenerateMojo mojo = new GenerateMojo();
        mojo.engine = GenerateMojo.createVelocityEngine();

        return mojo;
    }

    static SObjectDescription createSObjectDescription(final String name) throws IOException {
        try (InputStream inputStream = CamelSalesforceMojoOutputTest.class.getResourceAsStream("/" + name)) {
            final ObjectMapper mapper = JsonUtils.createObjectMapper();

            return mapper.readValue(inputStream, SObjectDescription.class);
        }
    }

    static Object[] testCase(final String json, final String source) throws IOException {
        return testCase(json, source, source, String::valueOf);
    }

    static Object[] testCase(final String json, final String source, final String expected,
        final Consumer<GenerateMojo> mojoConfigurator) throws IOException {
        final GenerateMojo mojo = createMojo();
        mojoConfigurator.accept(mojo);

        return new Object[] {json, createSObjectDescription(json), source, expected, mojo};
    }
}
