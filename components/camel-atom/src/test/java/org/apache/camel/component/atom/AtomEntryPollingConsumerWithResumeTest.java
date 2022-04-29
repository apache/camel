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
package org.apache.camel.component.atom;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.resume.TransientResumeStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Unit test for AtomEntryPollingConsumer
 */
@DisabledOnOs(OS.AIX)
public class AtomEntryPollingConsumerWithResumeTest extends CamelTestSupport {

    @DisplayName("Tests whether the component can consume with resume API enabled")
    @Test
    void testCanConsumeWithResume() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);
        mock.assertIsSatisfied();
    }

    private Date getTestFilterDate() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

        try {
            return df.parse("2007-11-13 14:35:00 +0100");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("atom:file:src/test/data/feed.atom?splitEntries=true&delay=500")
                        .resumable().resumeStrategy(new TransientResumeStrategy(new UpdatedDateFilter(getTestFilterDate())))
                        .routeId("WithResume")
                        .to("mock:result");
            }
        };
    }
}
