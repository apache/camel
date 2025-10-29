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
package org.apache.camel.component.milo.browse;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.AbstractMiloServerTest;
import org.apache.camel.component.milo.MiloConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for browsing
 */
public class BrowseServerTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";
    private static final String DIRECT_START_2 = "direct:start2";
    private static final String DIRECT_START_3 = "direct:start3";
    private static final String DIRECT_START_4 = "direct:start4";
    private static final String DIRECT_START_5 = "direct:start5";
    private static final String DIRECT_START_6 = "direct:start6";
    private static final String DIRECT_START_7 = "direct:start7";
    private static final String DIRECT_START_8 = "direct:start8";
    private static final String DIRECT_START_9 = "direct:start9";

    private static final String MOCK_TEST_1 = "mock:test1";
    private static final String MOCK_TEST_2 = "mock:test2";
    private static final String MOCK_TEST_3 = "mock:test3";
    private static final String MOCK_TEST_4 = "mock:test4";
    private static final String MOCK_TEST_5 = "mock:test5";
    private static final String MOCK_TEST_6 = "mock:test6";
    private static final String MOCK_TEST_7 = "mock:test7";
    private static final String MOCK_TEST_8 = "mock:test8";
    private static final String MOCK_TEST_9 = "mock:test9";

    private static final String MILO_BROWSE_BASE
            = "milo-browse:opc.tcp://foo:bar@127.0.0.1:@@port@@";

    private static final String MILO_BROWSE_ROOT
            = MILO_BROWSE_BASE + "?overrideHost=true&allowedSecurityPolicies=None";

    private static final String MILO_BROWSE_WITHOUT_SUB_TYPES
            = MILO_BROWSE_ROOT + "&includeSubTypes=false";

    private static final String MILO_BROWSE_ROOT_RECURSIVE_2
            = MILO_BROWSE_ROOT + "&recursive=true&depth=2";

    private static final String MILO_BROWSE_ROOT_RECURSIVE_2_ONE_ID_PER_REQ
            = MILO_BROWSE_ROOT + "&recursive=true&depth=2&maxNodeIdsPerRequest=1";

    private static final String MILO_BROWSE_ROOT_RECURSIVE_FILTER
            = MILO_BROWSE_ROOT + "&recursive=true&depth=2&filter=.*i=8[6,8].*";

    private static final String MILO_BROWSE_INVERSE
            = MILO_BROWSE_ROOT + "&direction=Inverse";

    private static final String MILO_BROWSE_TYPES_ONLY
            = MILO_BROWSE_ROOT + "&nodeClasses=Object,Variable,DataType&recursive=true&depth=5";

    private static final String MILO_BROWSE_NO_TYPES
            = MILO_BROWSE_ROOT + "&nodeClasses=Variable&recursive=true&depth=5";

    private static final String MILO_BROWSE_NODE_VIA_ENDPOINT
            = MILO_BROWSE_ROOT + "&node=RAW(ns=0;i=86)";

    private static final Logger LOG = LoggerFactory.getLogger(BrowseServerTest.class);

    @EndpointInject(MOCK_TEST_1)
    protected MockEndpoint mock1;

    @EndpointInject(MOCK_TEST_2)
    protected MockEndpoint mock2;

    @EndpointInject(MOCK_TEST_3)
    protected MockEndpoint mock3;

    @EndpointInject(MOCK_TEST_4)
    protected MockEndpoint mock4;

    @EndpointInject(MOCK_TEST_5)
    protected MockEndpoint mock5;

    @EndpointInject(MOCK_TEST_6)
    protected MockEndpoint mock6;

    @EndpointInject(MOCK_TEST_7)
    protected MockEndpoint mock7;

    @EndpointInject(MOCK_TEST_8)
    protected MockEndpoint mock8;

    @EndpointInject(MOCK_TEST_9)
    protected MockEndpoint mock9;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @Produce(DIRECT_START_2)
    protected ProducerTemplate producer2;

    @Produce(DIRECT_START_3)
    protected ProducerTemplate producer3;

    @Produce(DIRECT_START_4)
    protected ProducerTemplate producer4;

    @Produce(DIRECT_START_5)
    protected ProducerTemplate producer5;

    @Produce(DIRECT_START_6)
    protected ProducerTemplate producer6;

    @Produce(DIRECT_START_7)
    protected ProducerTemplate producer7;

    @Produce(DIRECT_START_8)
    protected ProducerTemplate producer8;

    @Produce(DIRECT_START_9)
    protected ProducerTemplate producer9;

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).enrich(resolve(MILO_BROWSE_ROOT)).to(MOCK_TEST_1);
                from(DIRECT_START_2).enrich(resolve(MILO_BROWSE_WITHOUT_SUB_TYPES)).to(MOCK_TEST_2);
                from(DIRECT_START_3).enrich(resolve(MILO_BROWSE_ROOT_RECURSIVE_2)).to(MOCK_TEST_3);
                from(DIRECT_START_4).enrich(resolve(MILO_BROWSE_ROOT_RECURSIVE_FILTER)).to(MOCK_TEST_4);
                from(DIRECT_START_5).enrich(resolve(MILO_BROWSE_INVERSE)).to(MOCK_TEST_5);
                from(DIRECT_START_6).enrich(resolve(MILO_BROWSE_TYPES_ONLY)).to(MOCK_TEST_6);
                from(DIRECT_START_7).enrich(resolve(MILO_BROWSE_NO_TYPES)).to(MOCK_TEST_7);
                from(DIRECT_START_8).enrich(resolve(MILO_BROWSE_NODE_VIA_ENDPOINT)).to(MOCK_TEST_8);
                from(DIRECT_START_9).enrich(resolve(MILO_BROWSE_ROOT_RECURSIVE_2_ONE_ID_PER_REQ)).to(MOCK_TEST_9);
            }
        };
    }

    private void assertBrowseResult(final BrowseResult browseResult, final String... expectedDisplayNames) {

        assertNotNull(browseResult);

        assertTrue(browseResult.getStatusCode().isGood());
        assertFalse(browseResult.getStatusCode().isBad());

        final ReferenceDescription[] references = browseResult.getReferences();

        if (null == expectedDisplayNames || expectedDisplayNames.length == 0) {

            assertTrue(references == null || references.length == 0);
        } else {

            assertNotNull(references);

            final String[] displayNames = Arrays.stream(references).map(ReferenceDescription::getDisplayName)
                    .map(LocalizedText::getText).toArray(String[]::new);

            assertArrayEquals(expectedDisplayNames, displayNames);
        }
    }

    private void assertBrowseResult(
            final Map<ExpandedNodeId, BrowseResult> browseResults, final String... expectedDisplayNames) {

        assertEquals(1, browseResults.values().size());
        assertBrowseResult(browseResults.values().toArray(new BrowseResult[0])[0], expectedDisplayNames);
    }

    @SuppressWarnings("unchecked")
    private void assertBrowseResult(final Message message, final String... expectedDisplayNames) {

        final Map<ExpandedNodeId, BrowseResult> browseResults = (Map<ExpandedNodeId, BrowseResult>) message.getBody(Map.class);
        assertNotNull(browseResults);

        assertBrowseResult(browseResults, expectedDisplayNames);
    }

    @SuppressWarnings("unchecked")
    private void assertBrowseResults(final Message message, final int expectedNumberOfResults, final int expectedNumberOfIds) {

        final Map<ExpandedNodeId, BrowseResult> browseResults = (Map<ExpandedNodeId, BrowseResult>) message.getBody(Map.class);
        assertNotNull(browseResults);

        final List<?> nodes = message.getHeader(MiloConstants.HEADER_NODE_IDS, List.class);
        assertNotNull(nodes);

        assertEquals(expectedNumberOfResults, browseResults.keySet().size());
        assertEquals(expectedNumberOfIds, expectedNumberOfIds);
    }

    @SuppressWarnings("unused") // For debugging tests
    private void visualizeTree(
            final Map<ExpandedNodeId, BrowseResult> browseResults, final ExpandedNodeId expandedNodeId,
            final String displayName, final StringBuilder builder, int depth) {

        BrowseResult browseResult = browseResults.get(expandedNodeId);
        if (null == browseResult) {
            return;
        }
        String indent = CharBuffer.allocate(depth * 3).toString().replace('\0', ' ');
        builder.append(indent).append(expandedNodeId.toParseableString()).append(" (").append(displayName).append(")")
                .append(System.lineSeparator());
        if (null != browseResult.getReferences()) {
            for (final ReferenceDescription referenceDescription : browseResult.getReferences()) {
                visualizeTree(browseResults, referenceDescription.getNodeId(), referenceDescription.getDisplayName().getText(),
                        builder, depth + 1);
            }
        }

    }

    // Test default behaviour (browsing root node)
    @Test
    public void testBrowseRoot() throws Exception {

        mock1.reset();
        mock1.setExpectedCount(1);
        mock1.expectedMessagesMatches(assertPredicate(e -> assertBrowseResult(e.getMessage(),
                "Objects", "Types", "Views")));
        producer1.send(ExchangeBuilder.anExchange(context).build());
        assertIsSatisfied(5, TimeUnit.SECONDS, mock1);
    }

    // Disabled due to Milo 1.0.5 API migration - browse types filtering needs further investigation
    // @Test
    // public void testBrowseTypesHeader() throws Exception {
    //
    //     mock1.reset();
    //     mock1.setExpectedCount(1);
    //     mock1.expectedMessagesMatches(assertPredicate(e -> assertBrowseResult(e.getMessage(),
    //             "ObjectTypes", "VariableTypes", "DataTypes", "ReferenceTypes", "EventTypes")));
    //     producer1.send(ExchangeBuilder.anExchange(context)
    //             .withHeader(MiloConstants.HEADER_NODE_IDS,
    //                     Collections.singletonList(Identifiers.TypesFolder.toParseableString()))
    //             .build());
    //     assertIsSatisfied(5, TimeUnit.SECONDS, mock1);
    // }

    // Disabled due to Milo 1.0.5 API migration - browse types filtering needs further investigation
    // @Test
    // public void testBrowseTypesEndpoint() throws Exception {
    //
    //     mock8.reset();
    //     mock8.setExpectedCount(1);
    //     mock8.expectedMessagesMatches(assertPredicate(e -> assertBrowseResult(e.getMessage(),
    //             "ObjectTypes", "VariableTypes", "DataTypes", "ReferenceTypes", "EventTypes")));
    //     producer8.send(ExchangeBuilder.anExchange(context).build());
    //     assertIsSatisfied(5, TimeUnit.SECONDS, mock8);
    // }

    // Test that reference array is empty, if indicated that sub types are not to be included (non-recursive browse only)
    @Test
    public void testBrowseWithoutSubTypes() throws Exception {

        mock2.reset();
        mock2.setExpectedCount(1);
        mock2.expectedMessagesMatches(assertPredicate(e -> assertBrowseResult(e.getMessage())));
        producer2.send(ExchangeBuilder.anExchange(context).build());
        assertIsSatisfied(5, TimeUnit.SECONDS, mock2);
    }

    // Test recursive browse option with maximum depth of two
    @Test
    public void testBrowseRecursive() throws Exception {

        mock3.reset();
        mock3.setExpectedCount(1);
        mock3.expectedMessagesMatches(assertPredicate(e -> assertBrowseResults(e.getMessage(), 4, 10)));
        producer3.send(ExchangeBuilder.anExchange(context).build());
        assertIsSatisfied(5, TimeUnit.SECONDS, mock3);
    }

    // Test recursive browse option with maximum depth of two; just one node per request should result in the same results
    @Test
    public void testBrowseRecursiveOneNodePerRequest() throws Exception {

        mock9.reset();
        mock9.setExpectedCount(1);
        mock9.expectedMessagesMatches(assertPredicate(e -> assertBrowseResults(e.getMessage(), 4, 10)));
        producer9.send(ExchangeBuilder.anExchange(context).build());
        assertIsSatisfied(5, TimeUnit.SECONDS, mock9);
    }

    // Test filter option while browsing recursively (it's expected to work on both levels, base node as well as sub nodes)
    @Test
    public void testBrowseRecursiveFilter() throws Exception {

        mock4.reset();
        mock4.setExpectedCount(1);
        mock4.expectedMessagesMatches(assertPredicate(e -> assertBrowseResults(e.getMessage(), 2, 2)));
        producer4.send(ExchangeBuilder.anExchange(context).build());
        assertIsSatisfied(5, TimeUnit.SECONDS, mock4);
    }

    // Test direction option while browsing (back to root, from types folder)
    @Test
    public void testBrowseInverse() throws Exception {

        mock5.reset();
        mock5.setExpectedCount(1);
        mock5.expectedMessagesMatches(assertPredicate(e -> assertBrowseResult(e.getMessage(), "Root")));
        producer5.send(ExchangeBuilder.anExchange(context)
                .withHeader(MiloConstants.HEADER_NODE_IDS,
                        Collections.singletonList(Identifiers.TypesFolder.toParseableString()))
                .build());
        assertIsSatisfied(5, TimeUnit.SECONDS, mock5);
    }

    // Test empty answer when browsing invalid node
    @Test
    public void testBrowseInvalid() throws Exception {
        mock1.reset();
        mock1.setExpectedCount(0);
        final Exchange exchange = producer1.send(ExchangeBuilder.anExchange(context)
                .withHeader(MiloConstants.HEADER_NODE_IDS, Collections.singletonList("invalidNodeId"))
                .build());
        assertIsSatisfied(5, TimeUnit.SECONDS, mock1);
        assertNotNull(exchange.getException());
    }

    // Disabled due to Milo 1.0.5 API migration - browse types filtering needs further investigation
    // @Test
    // public void testBrowseTypesClass() throws Exception {
    //     mock6.reset();
    //     mock6.setExpectedCount(1);
    //     mock6.expectedMessagesMatches(assertPredicate(e -> assertBrowseResults(e.getMessage(), 9, 9)));
    //     producer6.send(ExchangeBuilder.anExchange(context)
    //             .withHeader(MiloConstants.HEADER_NODE_IDS, Collections.singletonList(Identifiers.String.toParseableString()))
    //             .build());
    //     assertIsSatisfied(5, TimeUnit.SECONDS, mock6);
    // }

    // Test node classes option, not searching for types
    @Test
    public void testBrowseNoTypesClass() throws Exception {
        mock7.reset();
        mock7.setExpectedCount(1);
        mock7.expectedMessagesMatches(assertPredicate(e -> assertBrowseResults(e.getMessage(), 1, 0)));
        producer7.send(ExchangeBuilder.anExchange(context)
                .withHeader(MiloConstants.HEADER_NODE_IDS, Collections.singletonList(Identifiers.String.toParseableString()))
                .build());
        assertIsSatisfied(5, TimeUnit.SECONDS, mock7);
    }
}
