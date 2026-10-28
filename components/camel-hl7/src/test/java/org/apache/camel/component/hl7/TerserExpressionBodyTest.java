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
package org.apache.camel.component.hl7;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import ca.uhn.hl7v2.model.v24.message.ADT_A01;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.component.hl7.HL7.hl7terser;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TerserExpressionBodyTest extends CamelTestSupport {

    private static final String PATIENT_ID = "123456";

    @TempDir
    static Path inbox;

    @Test
    public void testStreamBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(PATIENT_ID);

        template.sendBody("direct:start", new ByteArrayInputStream(createADT01Message().getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testStreamCacheBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(PATIENT_ID);

        template.sendBody("direct:start", new InputStreamCache(createADT01Message().getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFileBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:file");
        mock.expectedBodiesReceived(PATIENT_ID);

        Files.writeString(inbox.resolve("adt.hl7"), createADT01Message());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testNullBody() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", null));
        ExpressionEvaluationException cause = assertInstanceOf(ExpressionEvaluationException.class, e.getCause());
        assertTrue(cause.getMessage().contains("is null"), cause.getMessage());
    }

    @Test
    public void testMapBody() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", Map.of("PID", PATIENT_ID)));
        ExpressionEvaluationException cause = assertInstanceOf(ExpressionEvaluationException.class, e.getCause());
        assertTrue(cause.getMessage().contains("java.util."), cause.getMessage());
        assertTrue(cause.getMessage().contains("HAPI Message"), cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").transform(hl7terser("PID-3-1")).to("mock:result");
                from("file:" + inbox + "?initialDelay=0&delay=10").transform(hl7terser("PID-3-1")).to("mock:file");
            }
        };
    }

    private static String createADT01Message() throws Exception {
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");
        adt.getPID().getPatientIdentifierList(0).getID().setValue(PATIENT_ID);
        return adt.encode();
    }
}
