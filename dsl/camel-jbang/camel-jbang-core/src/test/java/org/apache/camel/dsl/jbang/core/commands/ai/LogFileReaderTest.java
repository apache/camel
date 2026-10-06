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

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileReaderTest {

    private static final List<String> LINES = List.of(
            "2026-09-12 10:00:00.001  INFO 42 --- [           main] org.apache.camel.main.MainSupport : Apache Camel 4.23.0 is starting",
            "2026-09-12 10:00:01.002  WARN 42 --- [           main] o.a.c.impl.engine.AbstractCamelContext : Routes startup (started:1)",
            "2026-09-12 10:00:02.003 ERROR 42 --- [ timer://tick] org.apache.camel.processor.errorhandler.DefaultErrorHandler : Failed delivery",
            "java.lang.IllegalStateException: boom",
            "\tat org.example.Foo.bar(Foo.java:12)",
            "\tat org.example.Foo.baz(Foo.java:34)",
            "2026-09-12 10:00:03.004  INFO 42 --- [ timer://tick] route1 : Hello Camel");

    @Test
    void groupsContinuationLinesIntoTheRecordBeforeThemNewestFirst() {
        JsonObject result = LogFileReader.build(LINES, 50, null, null, new JsonObject());
        assertEquals(7, result.getInteger("totalLines"));
        assertEquals(4, result.getInteger("returnedLines"));
        List<JsonObject> rows = List.copyOf(result.getCollection("lines"));
        assertEquals("Hello Camel", rows.get(0).getString("message"));
        assertEquals("route1", rows.get(0).getString("logger"));
        assertEquals("10:00:03.004", rows.get(0).getString("time"));
        JsonObject error = rows.get(1);
        assertEquals("ERROR", error.getString("level"));
        assertEquals("DefaultErrorHandler", error.getString("logger"), "the logger is shortened to its class");
        assertTrue(error.getString("detail").startsWith("java.lang.IllegalStateException: boom\n\tat"));
        assertNull(rows.get(0).get("detail"));
    }

    @Test
    void foldsAStormOfTheSameLineIntoOneRecord() {
        List<String> storm = new java.util.ArrayList<>();
        storm.add(LINES.get(0));
        for (int i = 0; i < 100; i++) {
            storm.add("2026-09-12 10:01:" + String.format("%02d", i % 60)
                      + ".000 ERROR 42 --- [ timer://tick] route1 : Failed to call the API");
        }
        storm.add(LINES.get(6));

        JsonObject result = LogFileReader.build(storm, 50, null, null, new JsonObject());
        assertEquals(3, result.getInteger("returnedLines"), "the storm is one record between the two others");
        List<JsonObject> rows = List.copyOf(result.getCollection("lines"));
        JsonObject folded = rows.get(1);
        assertEquals(100, folded.getLong("repeated"));
        assertEquals("10:01:00.000", folded.getString("firstTime"), "when the storm started");
        assertEquals("10:01:39.000", folded.getString("time"), "the newest occurrence");
        assertNull(rows.get(0).get("repeated"), "a line that happened once has no repeat count");
    }

    @Test
    void filtersByLevelAndTextAndHonoursTheLimit() {
        JsonObject errors = LogFileReader.build(LINES, 50, null, "error", new JsonObject());
        assertEquals(1, errors.getInteger("returnedLines"));
        JsonObject boom = LogFileReader.build(LINES, 50, "BOOM", null, new JsonObject());
        assertEquals(1, boom.getInteger("returnedLines"), "the filter also matches the detail block");
        assertEquals("Failed delivery", ((JsonObject) boom.getCollection("lines").iterator().next())
                .getString("message"));
        assertEquals(2, LogFileReader.build(LINES, 2, null, null, new JsonObject()).getInteger("returnedLines"));
        assertEquals(0, LogFileReader.build(List.of(), 2, null, null, new JsonObject()).getInteger("returnedLines"));
    }

    @Test
    void withoutDetailsAnErrorKeepsItsFirstLineAndTheCause() {
        JsonObject result = LogFileReader.build(LINES, 50, null, null, false, new JsonObject());
        List<JsonObject> rows = List.copyOf(result.getCollection("lines"));
        JsonObject error = rows.get(1);
        assertEquals("ERROR", error.getString("level"));
        assertEquals("Failed delivery", error.getString("message"));
        assertNull(error.get("detail"), "the stack trace is left out");
        assertEquals(3, error.getInteger("detailLines"));
        assertEquals("java.lang.IllegalStateException: boom", error.getString("cause"),
                "the first line does not name the exception, so the cause does");
        assertTrue(result.getString("note").contains("details=true"));
        for (JsonObject row : rows) {
            assertTrue(row.keySet().stream().noneMatch(k -> k.startsWith("_")), "no internal field leaks: " + row);
        }
    }

    @Test
    void theErrorHandlerLineAlreadyNamesTheExceptionSoThereIsNoCause() {
        List<String> lines = List.of(
                "2026-10-03 08:29:42.988 ERROR 42 --- [- timer://stock] or.errorhandler.DefaultErrorHandler : Failed delivery"
                                     + " for (MessageId: A on ExchangeId: A) at stock-check[throwException1] cb.camel.yaml:27."
                                     + " Exhausted after delivery attempt: 1 caught: java.net.ConnectException: supplier"
                                     + " unreachable",
                "Message History (source location and message history is enabled)",
                "---------------------------------------------------------------------------------------------------",
                "Source                    ID                             Processor                      Elapsed (ms)",
                "cb.camel.yaml:4           stock-check/stock-check        from[timer://stock?period=1000]           0",
                "cb.camel.yaml:27          stock-check/throwException1    throwException[java.net.ConnectException] 0",
                "Stacktrace",
                "---------------------------------------------------------------------------------------------------",
                "java.net.ConnectException: supplier unreachable",
                "\tat org.apache.camel.processor.ThrowExceptionProcessor.process(ThrowExceptionProcessor.java:68)",
                "\tat org.apache.camel.processor.Pipeline.process(Pipeline.java:163)");

        JsonObject error = (JsonObject) LogFileReader.build(lines, 50, null, null, false, new JsonObject())
                .getCollection("lines").iterator().next();
        assertTrue(error.getString("message").endsWith("caught: java.net.ConnectException: supplier unreachable"));
        assertNull(error.get("detail"));
        assertNull(error.get("cause"), "the first line has it already");
        assertNull(error.get("at"), "a YAML route has no frame of the user's code; the first line has the source line");
        assertEquals(10, error.getInteger("detailLines"));
    }

    @Test
    void theCauseIsTheLastCausedByAlsoPastTheLinesThatAreKept() {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("2026-10-03 08:00:00.000 ERROR 42 --- [ main] route1 : Failed to start");
        lines.add("org.apache.camel.RuntimeCamelException: wrapped");
        for (int i = 0; i < 30; i++) {
            lines.add("\tat org.example.Frame.m" + i + "(Frame.java:" + i + ")");
        }
        lines.add("Caused by: java.io.IOException: disk full");
        lines.add("\tat org.example.Disk.write(Disk.java:1)");

        JsonObject error = (JsonObject) LogFileReader.build(lines, 50, null, null, false, new JsonObject())
                .getCollection("lines").iterator().next();
        assertEquals("java.io.IOException: disk full", error.getString("cause"));
        assertEquals(33, error.getInteger("detailLines"));
    }

    @Test
    void aPrettyPrintedBodyIsNotAStackTraceAndStays() {
        List<String> lines = List.of(
                "2026-10-03 08:00:00.000  INFO 42 --- [ timer://order] route1 : Order ORD-1001: {",
                "  \"orderId\" : \"ORD-1001\",",
                "  \"country\" : \"DK\"",
                "}");

        JsonObject result = LogFileReader.build(lines, 50, null, null, false, new JsonObject());
        JsonObject row = (JsonObject) result.getCollection("lines").iterator().next();
        assertTrue(row.getString("detail").contains("\"orderId\" : \"ORD-1001\""));
        assertNull(row.get("detailLines"));
        assertNull(result.get("note"), "nothing was left out");
    }

    @Test
    void withDetailsTheRecordIsAsBeforeAndTheFilterSearchesTheLeftOutTrace() {
        JsonObject error = (JsonObject) LogFileReader.build(LINES, 50, null, "error", true, new JsonObject())
                .getCollection("lines").iterator().next();
        assertTrue(error.getString("detail").startsWith("java.lang.IllegalStateException: boom"));
        assertNull(error.get("detailLines"));
        assertNull(error.get("cause"));

        JsonObject boom = LogFileReader.build(LINES, 50, "boom", null, false, new JsonObject());
        assertEquals(1, boom.getInteger("returnedLines"), "the filter matches the trace even when it is left out");
    }

    @Test
    void theOriginIsTheUsersFrameOfTheRootCauseUnderTheWrapping() {
        List<String> lines = List.of(
                "2026-10-03 09:00:00.000 ERROR 42 --- [ timer://tick] route1 : Failed to invoke the bean",
                "org.apache.camel.RuntimeCamelException: Error invoking method",
                "\tat org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException(ObjectHelper.java:21)",
                "\tat org.apache.camel.component.bean.BeanProcessor.process(BeanProcessor.java:81)",
                "Caused by: java.lang.reflect.InvocationTargetException",
                "\tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:118)",
                "\t... 2 more",
                "Caused by: java.lang.NullPointerException",
                "\tat app//camel.example.OrderBean.total(OrderBean.java:42)",
                "\tat app//camel.example.OrderBean.process(OrderBean.java:17)",
                "\t... 4 more");

        JsonObject error = (JsonObject) LogFileReader.build(lines, 50, null, null, false, new JsonObject())
                .getCollection("lines").iterator().next();
        assertEquals("java.lang.NullPointerException", error.getString("cause"));
        assertEquals("camel.example.OrderBean.total(OrderBean.java:42)", error.getString("at"),
                "the top frame of the root cause, without its class loader prefix");
    }

    @Test
    void aRootCauseCutShortByMoreHandsOverToTheExceptionAboveIt() {
        List<String> lines = List.of(
                "2026-10-03 09:00:00.000 ERROR 42 --- [ timer://tick] route1 : Order failed",
                "java.lang.IllegalStateException: order failed",
                "\tat camel.example.OrderBean.process(OrderBean.java:17)",
                "\tat org.apache.camel.component.bean.BeanProcessor.process(BeanProcessor.java:81)",
                "Caused by: java.lang.NumberFormatException: For input string: \"x\"",
                "\tat java.base/java.lang.Integer.parseInt(Integer.java:661)",
                "\t... 2 more");

        JsonObject error = (JsonObject) LogFileReader.build(lines, 50, null, null, false, new JsonObject())
                .getCollection("lines").iterator().next();
        assertEquals("java.lang.NumberFormatException: For input string: \"x\"", error.getString("cause"));
        assertEquals("camel.example.OrderBean.process(OrderBean.java:17)", error.getString("at"),
                "the JDK frame of the root cause is not the user's code, and the frame that called it is above");
    }

    @Test
    void aGroovyScriptIsTheUsersCode() {
        List<String> lines = List.of(
                "2026-10-03 09:00:00.000  WARN 42 --- [ timer://orders] TimerConsumer : Error processing exchange",
                "groovy.lang.MissingPropertyException: No such property: header for class: Script1",
                "\tat org.codehaus.groovy.runtime.ScriptBytecodeAdapter.unwrap(ScriptBytecodeAdapter.java:65)",
                "\tat Script1.run(Script1.groovy:3)",
                "\tat org.apache.camel.language.groovy.GroovyExpression.evaluate(GroovyExpression.java:72)");

        JsonObject warn = (JsonObject) LogFileReader.build(lines, 50, null, null, false, new JsonObject())
                .getCollection("lines").iterator().next();
        assertEquals("Script1.run(Script1.groovy:3)", warn.getString("at"));
        assertEquals("groovy.lang.MissingPropertyException: No such property: header for class: Script1",
                warn.getString("cause"));
    }
}
