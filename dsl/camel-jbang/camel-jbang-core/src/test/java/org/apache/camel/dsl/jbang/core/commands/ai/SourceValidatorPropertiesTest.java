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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24698: a camel.component/dataformat/language key for a name the catalog does not know is reported (camel run
 * fails on it with "Cannot auto create component"), with the closest real name.
 */
public class SourceValidatorPropertiesTest {

    private final CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    void unknownComponentInAKeyIsReportedWithTheClosestName() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.main.name=x
                camel.component.logger.level=INFO
                camel.dataformat.jacksn.prettyPrint=true
                """, catalog, null);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).startsWith("Line 2: logger    Unknown component").contains("did you mean log?");
        assertThat(msgs.get(1)).startsWith("Line 3: jacksn    Unknown dataformat").contains("did you mean jackson");
    }

    @Test
    void wrongMainKeyGetsTheClosestOption() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.main.duration=3600s
                camel.main.stream-caching-enabled=true
                camel.main.streamCaching=true
                """, catalog, null);
        // camel.main.duration is an option since CAMEL-24706 (3600s is a valid value)
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("streamCaching").contains("did you mean").contains("camel.main.streamCachingEnabled");
    }

    @Test
    void optionOfAnotherGroupIsPointedToThatGroup() {
        // a REST binding mode set on the HTTP component, or on camel.main: both fail at startup
        List<String> msgs = SourceValidator.validateProperties("""
                camel.component.netty-http.binding-mode=json
                camel.main.binding-mode=json
                """, catalog, null);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).startsWith("Line 1: binding-mode").contains("Unknown option")
                .contains("did you mean camel.rest.bindingMode?")
                .contains("binding-mode is an option of camel.rest, not of the camel.component.netty-http key");
        assertThat(msgs.get(1)).startsWith("Line 2:").contains("did you mean camel.rest.bindingMode?");
    }

    @Test
    void anOptionOfSeveralGroupsNamesTheLikelyOneFirst() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.component.netty-http.port=8080
                """, catalog, null);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("did you mean camel.rest.port?")
                .contains("port is an option of camel.rest, camel.server or camel.management");
    }

    @Test
    void proseAfterThePropertiesIsNamed() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.main.name=demo

                == How to run ==

                bash
                camel run demo.camel.yaml

                The route fires once and exits.
                """, catalog, null);
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0)).startsWith("Line 3 is not a property (\"== How to run ==\")").contains("# comment");
        assertThat(msgs.get(1)).startsWith("Line 6 is not a property (\"camel run demo.camel.yaml\")");
        assertThat(msgs.get(2)).startsWith("Line 8 is not a property (\"The route fires once and exits.\")");
    }

    @Test
    void inventedMainKeyGetsTheOptionsThatStartWithTheSameWord() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.main.duration-style=seconds
                """, catalog, null);
        assertThat(msgs).hasSize(1);
        // since CAMEL-24706 the closest option is the camel.main.duration alias itself
        assertThat(msgs.get(0)).contains("duration-style").contains("did you mean camel.main.duration");
    }

    @Test
    void aLongExplanationIsReportedAsThreeLinesAndACount() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.main.name=demo

                **How to run:**
                1. Place all three files in the same directory
                2. Run from that directory
                **What this example does:**
                - Timer trigger fires every 5 seconds
                - Bean definition declares the processor
                """, catalog, null);
        assertThat(msgs).hasSize(4);
        assertThat(msgs.get(3)).startsWith("Line 6 and the lines after it: more text that is not a property");
    }

    @Test
    void aLogLevelUnderCamelMainPointsToLoggingLevel() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.main.loggingLevel=INFO
                camel.main.log-level=DEBUG
                """, catalog, null);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).contains("logging.level.root=INFO").doesNotContain("camel.debug");
        assertThat(msgs.get(1)).contains("logging.level.root=INFO");
    }

    @Test
    void aGroupNameFoldedIntoAMainOptionPointsToTheGroup() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.main.restComponent=undertow
                camel.main.rest-port=8080
                """, catalog, null);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).contains("did you mean camel.rest.component?").contains("camel.rest.* keys");
        assertThat(msgs.get(1)).contains("did you mean camel.rest.port?");
    }

    @Test
    void aRootLevelAboveInfoSaysTheRouteLogsAreHiddenToo() {
        List<String> msgs = SourceValidator.validateProperties("""
                logging.level.root=WARN
                logging.level.org.apache.camel=INFO
                """, catalog, null);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 1: logging.level.root=WARN also hides the route's own log steps");
    }

    @Test
    void anInventedDurationSwitchSaysThereIsNone() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.main.duration-strict-check=false
                camel.main.durationLoggingEnabled=true
                """, catalog, null);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).contains("there is no such switch").contains("remove the line");
        assertThat(msgs.get(1)).contains("there is no such switch");
    }

    @Test
    void anEnabledSwitchOnAGroupWithoutOneSaysSo() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.resilience4j.enabled=true
                """, catalog, null);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("camel.resilience4j has no enabled switch").contains("bulkheadEnabled");
    }

    @Test
    void anEndpointOptionSetOnTheComponentSaysUri() {
        List<String> msgs = SourceValidator.validateProperties("""
                camel.component.timer.period=1000
                """, catalog, null);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("Unknown option").contains("period is an endpoint option of timer")
                .contains("timer:name?period=...");
    }
}
