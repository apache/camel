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
import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CamelSalesforceMojoOutputTest {
    private static final String TEST_CASE_FILE = "case.json";
    private static final String TEST_CALCULATED_FORMULA_FILE = "complex_calculated_formula.json";
    private static final String FIXED_DATE = "Thu Mar 09 16:15:49 ART 2017";

    private CamelSalesforceMojo mojo;
    private CamelSalesforceMojo.GeneratorUtility utility = new CamelSalesforceMojo.GeneratorUtility(false);

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Parameters(name = "json = {0}, source = {2}")
    public static Iterable<Object[]> parameters() throws IOException {
        return Arrays.asList(pair(TEST_CASE_FILE, "Case.java"),
            pair(TEST_CASE_FILE, "Case_PickListAccentMarkEnum.java"),
            pair(TEST_CASE_FILE, "Case_PickListQuotationMarkEnum.java"),
            pair(TEST_CASE_FILE, "Case_PickListSlashEnum.java"), pair(TEST_CASE_FILE, "QueryRecordsCase.java"),
            pair(TEST_CALCULATED_FORMULA_FILE, "ComplexCalculatedFormula.java"),
            pair(TEST_CALCULATED_FORMULA_FILE, "QueryRecordsComplexCalculatedFormula.java"));
    }

    static Object[] pair(String json, String source) throws IOException {
        return new Object[] {json, createSObjectDescription(json), source};
    }

    @Parameter(0)
    public String json;

    @Parameter(1)
    public SObjectDescription description;

    @Parameter(2)
    public String source;

    @Before
    public void setUp() throws Exception {
        mojo = new CamelSalesforceMojo();
        mojo.engine = CamelSalesforceMojo.createVelocityEngine();
    }

    @Test
    public void testProcessDescriptionPickLists() throws Exception {
        final File pkgDir = temp.newFolder();

        mojo.processDescription(pkgDir, description, utility, FIXED_DATE);

        File generatedFile = new File(pkgDir, source);
        String generatedContent = FileUtils.readFileToString(generatedFile);

        String expectedContent = IOUtils
            .toString(CamelSalesforceMojoOutputTest.class.getResource("/generated/" + source));

        Assert.assertEquals(
            "Geberated source file in " + source + " must be equal to the one present in test/resources",
            generatedContent, expectedContent);
    }

    static SObjectDescription createSObjectDescription(String name) throws IOException {
        InputStream inputStream = CamelSalesforceMojoOutputTest.class.getResourceAsStream("/" + name);
        ObjectMapper mapper = JsonUtils.createObjectMapper();

        return mapper.readValue(inputStream, SObjectDescription.class);
    }

}
