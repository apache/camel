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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CamelSalesforceMojoOutputTest {
    private static final String TEST_CASE_FILE = "case.json";
    private static final String TEST_INVOICE_FILE = "blng__Invoice__c.json";
    private static final String TEST_PAYMENT_FILE = "blng__Payment__c.json";
    private static final String TEST_CALCULATED_FORMULA_FILE = "complex_calculated_formula.json";
    private static final String FIXED_DATE = "Thu Mar 09 16:15:49 ART 2017";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    public String json;
    
    public String source;

    Map<String, SObjectDescription> descriptions;
    
    private CamelSalesforceMojo mojo;
    private CamelSalesforceMojo.GeneratorUtility utility;
    
    public CamelSalesforceMojoOutputTest(String json, Map<String, SObjectDescription> descriptions, String source) {
        this.json = json;
        this.descriptions = descriptions;
        this.source = source;
    }

    @Parameters(name = "json = {0}, source = {2}")
    public static Collection<Object[]> parameters() throws IOException {
        return Arrays.asList(
                             pair("Case.java", TEST_CASE_FILE),
                             pair("Case_PickListAccentMarkEnum.java", TEST_CASE_FILE),
                             pair("Case_PickListQuotationMarkEnum.java", TEST_CASE_FILE),
                             pair("Case_PickListSlashEnum.java", TEST_CASE_FILE), 
                             pair("QueryRecordsCase.java", TEST_CASE_FILE),
                             pair("ComplexCalculatedFormula.java", TEST_CALCULATED_FORMULA_FILE),
                             pair("QueryRecordsComplexCalculatedFormula.java", TEST_CALCULATED_FORMULA_FILE),
                             pair("blng__Payment__c.java", TEST_PAYMENT_FILE, TEST_INVOICE_FILE),
                             pair("blng__Invoice__c_Lookup.java", TEST_PAYMENT_FILE, TEST_INVOICE_FILE)
            );
    }

    static Object[] pair(String source, String... jsons) throws IOException {
        return new Object[] {jsons[0], createSObjectsDescriptions(jsons), source};
    }

    @Before
    public void setUp() throws Exception {
        mojo = new CamelSalesforceMojo();
        mojo.engine = CamelSalesforceMojo.createVelocityEngine();
        mojo.descriptions = descriptions;
        utility = new CamelSalesforceMojo.GeneratorUtility(false, mojo);
    }

    @Test
    public void testProcessDescriptionPickLists() throws Exception {
        final File pkgDir = temp.newFolder();

        mojo.processDescription(pkgDir, descriptions.get(removeJsonFileExtension(json)), utility, FIXED_DATE);

        File generatedFile = new File(pkgDir, source);
        String generatedContent = FileUtils.readFileToString(generatedFile, StandardCharsets.UTF_8);

        String expectedContent = IOUtils
            .toString(CamelSalesforceMojoOutputTest.class.getResource("/generated/" + source), StandardCharsets.UTF_8);

        Assert.assertEquals(
            "Generated source file in " + source + " must be equal to the one present in test/resources",
            expectedContent, generatedContent);
    }
    
    static String removeJsonFileExtension(String name) {
        return name.replace(".json", "");
    }

    static Map<String, SObjectDescription> createSObjectsDescriptions(String... names) throws IOException {
        Map<String, SObjectDescription> descriptions = new HashMap<>();
        for (String name : names) {
            try (InputStream inputStream = CamelSalesforceMojoOutputTest.class.getResourceAsStream("/" + name)) {
                ObjectMapper mapper = JsonUtils.createObjectMapper();
                descriptions.put(removeJsonFileExtension(name), mapper.readValue(inputStream, SObjectDescription.class));
            }
        }
        return descriptions;
    }
}
