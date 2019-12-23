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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient.ResponseCallback;
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
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
public class CamelSalesforceMojoOutputTest {
    private static final String TEST_CALCULATED_FORMULA_FILE = "complex_calculated_formula.json";
    private static final String TEST_CASE_FILE = "case.json";

    @Parameter(1)
    public SObjectDescription description;

    @Parameter(4)
    public Function<String, String> fileNameAdapter = Function.identity();

    @Parameter(0)
    public String json;

    @Parameter(3)
    public GenerateMojo mojo;

    @Parameter(2)
    public Set<String> sources;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testProcessDescription() throws Exception {
        final File pkgDir = temp.newFolder();
        final GenerateMojo.GeneratorUtility utility = mojo.new GeneratorUtility();

        final RestClient client = mockRestClient();

        mojo.descriptions = new ObjectDescriptions(client, 0, null, null, null, null, mojo.getLog());

        Set<String> sObjectNames = StreamSupport.stream(mojo.descriptions.fetched().spliterator(), false).map(SObjectDescription::getName).collect(Collectors.toSet());

        mojo.processDescription(pkgDir, description, utility, sObjectNames);

        for (final String source : sources) {
            final String expected = fileNameAdapter.apply(source);

            final File generatedFile = new File(pkgDir, source);
            final String generatedContent = FileUtils.readFileToString(generatedFile, StandardCharsets.UTF_8);

            final String expectedContent = IOUtils.toString(CamelSalesforceMojoOutputTest.class.getResource("/generated/" + expected), StandardCharsets.UTF_8);

            Assert.assertEquals("Generated source file in " + source + " must be equal to the one present in test/resources/" + expected, expectedContent, generatedContent);
        }
    }

    @Parameters(name = "json = {0}, source = {2}")
    public static Iterable<Object[]> parameters() throws IOException {
        return Arrays.asList(testCase(TEST_CASE_FILE, "Case.java"), testCase(TEST_CASE_FILE, "Case_PickListAccentMarkEnum.java"),
                             testCase(TEST_CASE_FILE, "Case_PickListQuotationMarkEnum.java"), testCase(TEST_CASE_FILE, "Case_PickListSlashEnum.java"), //
                             testCase(TEST_CASE_FILE, "QueryRecordsCase.java"), testCase(TEST_CALCULATED_FORMULA_FILE, "ComplexCalculatedFormula.java"),
                             testCase(TEST_CALCULATED_FORMULA_FILE, "QueryRecordsComplexCalculatedFormula.java"), testCase("asset.json", "Asset.java"), //
                             testCase("asset.json", mojo -> {
                                 mojo.customTypes = new HashMap<>();
                                 mojo.customTypes.put("date", "java.time.LocalDateTime");

                                 mojo.setup();
                             }, s -> "Asset_LocalDateTime.java", "Asset.java"), testCase("with_reference.json", "With_Reference__c.java"));
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

    static RestClient mockRestClient() {
        final RestClient client = mock(RestClient.class);
        doAnswer(provideResource("/global_sobjects.json")).when(client).getGlobalObjects(anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/account.json")).when(client).getDescription(eq("Account"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/asset.json")).when(client).getDescription(eq("Asset"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/case.json")).when(client).getDescription(eq("Case"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/invoice.json")).when(client).getDescription(eq("Invoice__c"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/line_item.json")).when(client).getDescription(eq("Line_Item__c"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/merchandise.json")).when(client).getDescription(eq("Merchandise__c"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/with_reference.json")).when(client).getDescription(eq("With_Reference__c"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/product2.json")).when(client).getDescription(eq("Product2"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/with_external_id.json")).when(client).getDescription(eq("With_External_Id__c"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/group.json")).when(client).getDescription(eq("Group"), anyMap(), any(ResponseCallback.class));
        doAnswer(provideResource("/user.json")).when(client).getDescription(eq("User"), anyMap(), any(ResponseCallback.class));
        return client;
    }

    static Answer<Void> provideResource(final String resource) {
        return invocation -> {
            final ResponseCallback callback = Arrays.stream(invocation.getArguments()).filter(ResponseCallback.class::isInstance).map(ResponseCallback.class::cast).findFirst()
                .get();

            callback.onResponse(CamelSalesforceMojoOutputTest.class.getResourceAsStream(resource), null, null);
            return null;
        };
    }

    static Object[] testCase(final String json, final Consumer<GenerateMojo> mojoConfigurator, final Function<String, String> adapter, final String... sources) throws IOException {
        final GenerateMojo mojo = createMojo();
        mojoConfigurator.accept(mojo);

        return new Object[] {json, createSObjectDescription(json), new HashSet<>(Arrays.asList(sources)), mojo, adapter};
    }

    static Object[] testCase(final String json, final Consumer<GenerateMojo> mojoConfigurator, final String... sources) throws IOException {
        return testCase(json, mojoConfigurator, Function.identity(), sources);
    }

    static Object[] testCase(final String json, final String... sources) throws IOException {
        return testCase(json, String::valueOf, sources);
    }
}
