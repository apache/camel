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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.exceptionhandler.MissingPluginParameterExceptionHandler;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticCommandTest {

    @TempDir
    Path home;
    private final long pid = ProcessHandle.current().pid();
    private final StringPrinter printer = new StringPrinter();
    private final StringWriter errors = new StringWriter();
    private ExecutorService executor;
    private Path previousHome;

    @BeforeEach
    void setup() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        Files.writeString(CommandLineHelper.getCamelDir().resolve(pid + "-status.json"),
                "{\"context\":{\"name\":\"semantic-test\",\"phase\":4}}");
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void stop() throws Exception {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void listsDefinitionsAndExperts() throws Exception {
        JsonObject response = json("""
                {"evaluations":[{"name":"safe","expert":"guard","operation":"detect",
                "state":"${body}","resultType":"boolean","error":"definition error"}],
                "experts":[{"reference":"guard","name":"Safety guard","error":"expert error"}],
                "defaultError":"No default expert"}
                """);
        AtomicReference<JsonObject> request = new AtomicReference<>();
        assertEquals(0, respond(new CamelSemantic(main()), r -> {
            request.set(r);
            return response;
        }));
        assertEquals("semantic-metadata", request.get().get("action"));
        assertEquals(true, request.get().get("overview"));
        assertTrue(printer.getOutput().contains("RESULT TYPE"));
        assertTrue(printer.getOutput().lines().anyMatch(line -> line.trim().matches(
                "safe +guard +detect +\\$\\{body} +boolean +definition error")));
        assertFalse(printer.getOutput().contains("\t"));
        assertTrue(printer.getOutput().contains("Safety guard"));
        assertTrue(printer.getOutput().contains("expert error"));
        assertTrue(printer.getOutput().contains("No default expert"));
        assertEquals("", errors.toString());
    }

    @Test
    void emitsMetadataAsOneJsonDocumentIncludingEmptyLists() throws Exception {
        JsonObject response = json("{\"evaluations\":[],\"experts\":[]}");
        assertEquals(0, respond(new CamelSemantic(main()), r -> response, "--json"));
        assertEquals(response, json(printer.getOutput()));
        assertEquals("", errors.toString());
    }

    @Test
    void showsExpertOperationAndParameterContract() throws Exception {
        JsonObject response = json("""
                {"operations":[{"name":"rank","description":"Rank text","resultType":"score",
                "contract":{"inputTypes":["text"],"parameters":[{"name":"criteria","type":"List",
                "itemType":"String","required":true}],"resultMeaning":"quality"}}]}
                """);
        assertEquals(0, respond(new CamelSemantic(main()), r -> {
            assertEquals("grader", r.get("expert"));
            assertFalse(r.containsKey("overview"));
            return response;
        }, "--expert=grader"));
        assertTrue(printer.getOutput().contains("rank (score): Rank text"));
        assertTrue(printer.getOutput().contains("criteria"));
        assertTrue(printer.getOutput().contains("quality"));
    }

    @Test
    void unavailableExpertReturnsNotFound() throws Exception {
        assertEquals(3, respond(new CamelSemantic(main()), r -> jsonUnchecked("{\"operations\":[]}"),
                "--expert=unknown", "--json"));
        assertError(3, "unknown");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedExpertLookupRetainsFailureExitCode(boolean json) throws Exception {
        assertEquals(1, respond(new CamelSemantic(main()), r -> jsonUnchecked(
                "{\"status\":\"failed\",\"error\":\"metadata dispatch failed\"}"),
                "--expert=guard", "--json=" + json));
        assertFalse(errors.toString().contains("NullPointerException"));
        if (json) {
            assertError(1, "metadata dispatch failed");
        } else {
            assertEquals("metadata dispatch failed" + System.lineSeparator(), errors.toString());
            assertEquals("", printer.getOutput());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void textParseErrorsKeepSuggestionsSynopsisAndHelp(boolean evaluate) throws Exception {
        SemanticActionCommand action = evaluate ? new SemanticEvaluate(main()) : new CamelSemantic(main());
        assertEquals(2, command(action).execute("--exper=guard"));
        assertTrue(errors.toString().contains("--expert"));
        assertTrue(errors.toString().contains("Usage:"));
        assertTrue(errors.toString().contains("--help'"));
        assertEquals("", printer.getOutput());
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void evaluatesNamedDefinitionWithSampleExchangeWithoutDoubleEscaping() throws Exception {
        String body = "A \"quote\"\nwith a backslash \\ and = sign";
        assertEquals(0, respond(new SemanticEvaluate(main()), r -> {
            assertEquals("semantic-evaluate", r.get("action"));
            assertEquals("safe", r.get("evaluation"));
            assertEquals(body, r.get("body"));
            assertEquals("a=b", r.getMap("headers").get("subject"));
            assertEquals(true, r.getMap("variables").get("enabled"));
            assertEquals(1000L, r.getLong("timeout"));
            return jsonUnchecked("{\"status\":\"success\",\"value\":false,\"elapsedMillis\":12}");
        }, "--evaluation=safe", "--body=" + body, "--header=subject=a=b", "--variable=enabled=json:true",
                "--evaluation-timeout=1000", "--json"));
        assertEquals(false, json(printer.getOutput()).get("value"));
        assertEquals("", errors.toString());
    }

    @Test
    void evaluatesDirectExpertWithTypedParameters() throws Exception {
        assertEquals(0, respond(new SemanticEvaluate(main()), r -> {
            assertEquals("grader", r.get("expert"));
            assertEquals("rank", r.get("operation"));
            assertEquals(List.of("one", "two"), r.get("input"));
            JsonObject parameters = r.getMap("parameters");
            assertEquals(List.of("poor", "good"), parameters.get("criteria"));
            assertEquals(0.7, parameters.getDouble("threshold"));
            assertEquals(true, parameters.get("strict"));
            assertEquals("plain text", parameters.get("description"));
            return jsonUnchecked("""
                    {"status":"success","value":0.8,"probability":0.9,"probabilities":{"good":0.9},
                    "confidence":0.85,"elapsedMillis":12}
                    """);
        }, "--expert=grader", "--operation=rank", "--input=json:[\"one\",\"two\"]",
                "--parameter=criteria=json:[\"poor\",\"good\"]", "--parameter=threshold=json:0.7",
                "--parameter=strict=json:true", "--parameter=description=plain text"));
        for (String key : List.of("value", "probability", "probabilities", "confidence", "elapsedMillis")) {
            assertTrue(printer.getOutput().contains(key + ":"));
        }
    }

    @Test
    void readsBodyFileAsText() throws Exception {
        Path input = home.resolve("sample.txt");
        Files.writeString(input, "json:literal\n");
        assertEquals(0, respond(new SemanticEvaluate(main()), r -> {
            assertEquals("json:literal\n", r.get("body"));
            return jsonUnchecked("{\"status\":\"success\",\"value\":true}");
        }, "--evaluation=safe", "--body=file:" + input));
    }

    @Test
    void providerFailureHasNonzeroExitAndRetainsTimingInJson() throws Exception {
        assertEquals(1, respond(new SemanticEvaluate(main()), r -> jsonUnchecked("""
                {"status":"failed","error":"provider unavailable","elapsedMillis":23}
                """), "--evaluation=safe", "--json"));
        assertError(1, "provider unavailable");
        assertEquals(23L, json(printer.getOutput()).getLong("elapsedMillis"));
    }

    @Test
    void providerFailureWithoutMessageStillHasDiagnostic() throws Exception {
        assertEquals(1, respond(new SemanticEvaluate(main()), r -> jsonUnchecked(
                "{\"status\":\"failed\",\"error\":null}"), "--evaluation=safe", "--json"));
        assertError(1, "Semantic action failed");
    }

    @Test
    void rejectsDuplicateParameterKeys() throws Exception {
        assertEquals(2, command(new SemanticEvaluate(main())).execute(Long.toString(pid), "--expert=grader",
                "--operation=rank", "--input=text", "--parameter=x=first", "--parameter=x=second", "--json"));
        assertError(2, "Duplicate --parameter key: x");
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void missingBodyFileIsUsageError() throws Exception {
        assertEquals(2, command(new SemanticEvaluate(main())).execute(Long.toString(pid), "--evaluation=safe",
                "--body=file:" + home.resolve("missing.txt"), "--json"));
        assertError(2, "Cannot read input file");
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void textUsageErrorsGoOnlyToStderr() throws Exception {
        assertEquals(2, command(new SemanticEvaluate(main())).execute(Long.toString(pid)));
        assertEquals("", printer.getOutput());
        assertTrue(errors.toString().contains("Choose --evaluation=<name>, or --expert with --operation and --input"));
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void unavailableConsoleReturnsNotFound() throws Exception {
        assertEquals(3, respond(new SemanticEvaluate(main()), r -> new JsonObject(), "--evaluation=safe", "--json"));
        assertError(3, "camel-semantic");
    }

    @Test
    void timeoutCleansRequestAndIgnoresLegacyReply() throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(pid + "-output.json"),
                "{\"status\":\"success\",\"value\":true}");
        assertEquals(4, command(new SemanticEvaluate(main())).execute(Long.toString(pid), "--evaluation=safe",
                "--timeout=150", "--json"));
        assertError(4, "No reply");
        assertTrue(actionFiles().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "--parameter=bad", "--parameter==value", "--parameter=x=json:[bad",
            "--variable=global:x=value", "--evaluation=", "--evaluation-timeout=50001", "--timeout=0",
            "--expert=other", "--unknown", "--timeout=not-a-number" })
    void rejectsBadUsageWithoutSendingAnAction(String invalid) throws Exception {
        assertEquals(2, command(new SemanticEvaluate(main())).execute(Long.toString(pid), "--evaluation=safe", invalid,
                "--json"));
        assertError(2, "");
        assertTrue(actionFiles().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = { "bad", "=value", "x=json:[bad", "x=json:1 2", "x=json:[1,]", "x=json:" })
    void rejectsMalformedDirectParameters(String parameter) throws Exception {
        assertEquals(2, command(new SemanticEvaluate(main())).execute(Long.toString(pid), "--expert=grader",
                "--operation=rank", "--input=text", "--parameter=" + parameter, "--json"));
        assertError(2, "");
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void requiresAnIntegrationStatusFileEvenForNumericPid() throws Exception {
        Files.delete(CommandLineHelper.getCamelDir().resolve(pid + "-status.json"));
        assertEquals(3, command(new CamelSemantic(main())).execute(Long.toString(pid), "--json"));
        assertError(3, "No running Camel integration");
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void rejectsAmbiguousIntegrationSelection() throws Exception {
        CamelSemantic semantic = new CamelSemantic(main()) {
            @Override
            List<Long> findPids(String name) {
                return List.of(pid, pid);
            }
        };
        assertEquals(3, command(semantic).execute("--json"));
        assertError(3, "Multiple Camel integrations");
        assertTrue(errors.toString().contains(Long.toString(pid)));
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void ioFailureIsInternalErrorRatherThanTimeout() throws Exception {
        CamelSemantic semantic = new CamelSemantic(main()) {
            @Override
            List<Long> findPids(String name) {
                CommandLineHelper.useHomeDir(home.resolve("missing").toString());
                return List.of(pid);
            }

            @Override
            JsonObject loadStatus(long id) {
                return jsonUnchecked("{\"context\":{\"phase\":4}}");
            }
        };
        assertEquals(70, command(semantic).execute(Long.toString(pid), "--json"));
        assertError(70, "");
        assertTrue(errors.toString().contains("NoSuchFileException"));
    }

    @Test
    void concurrentClientsReceiveOnlyTheirOwnResponse() throws Exception {
        StringPrinter secondPrinter = new StringPrinter();
        var first = CompletableFuture.supplyAsync(() -> command(new SemanticEvaluate(main())).execute(Long.toString(pid),
                "--evaluation=first", "--json"), executor);
        var second = CompletableFuture.supplyAsync(() -> command(new SemanticEvaluate(
                new CamelJBangMain()
                        .withPrinter(secondPrinter)))
                .execute(Long.toString(pid), "--evaluation=second", "--json"), executor);
        await().atMost(5, TimeUnit.SECONDS).until(() -> actionFiles().size() == 2);
        List<Path> requests = actionFiles();
        assertNotEquals(requests.get(0), requests.get(1));
        for (Path request : requests) {
            JsonObject action = json(Files.readString(request));
            JsonObject response = new JsonObject();
            response.put("status", "success");
            response.put("value", action.get("evaluation"));
            reply(request, response);
        }
        assertEquals(0, first.get(5, TimeUnit.SECONDS));
        assertEquals(0, second.get(5, TimeUnit.SECONDS));
        assertEquals("first", json(printer.getOutput()).get("value"));
        assertEquals("second", json(secondPrinter.getOutput()).get("value"));
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void mainRegistersSemanticCommandsAndFormatsParseErrorsAsJson() {
        CamelJBangMain main = new CamelJBangMain() {
            @Override
            public void postAddCommands(CommandLine command, String[] args) {
                CommandLine semantic = command.getSubcommands().get("semantic");
                assertNotNull(semantic);
                assertNotNull(semantic.getSubcommands().get("get"));
                assertNotNull(semantic.getSubcommands().get("eval"));
                assertNotNull(semantic.getSubcommands().get("audit"));
                assertFalse(command.getSubcommands().get("get").getSubcommands().containsKey("semantic"));
                assertFalse(command.getSubcommands().get("cmd").getSubcommands().containsKey("semantic-evaluate"));
            }

            @Override
            public void preExecute(CommandLine command, String[] args) {
                command.setErr(new PrintWriter(errors, true));
            }

            @Override
            public void quit(int code) {
                assertEquals(2, code);
            }
        };
        main.withPrinter(printer);
        main.execute("semantic", "eval", "--bad-option", "--json");
        assertError(2, "Unknown option");
    }

    @ParameterizedTest
    @CsvSource({ "false, true", "false, false", "true, true", "true, false" })
    void jsonParseErrorsWorkBeforeAndAfterInvalidOption(boolean evaluate, boolean jsonFirst) throws Exception {
        SemanticActionCommand action = evaluate ? new SemanticEvaluate(main()) : new CamelSemantic(main());
        String[] args = jsonFirst
                ? new String[] { "--json", "--exper=guard" }
                : new String[] { "--exper=guard", "--json" };
        assertEquals(2, command(action).execute(args));
        assertError(2, "Unknown option");
        assertFalse(errors.toString().contains("Usage:"));
        assertTrue(actionFiles().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = { "--json=false", "--" })
    void textParseErrorsDoNotEnableJson(String option) throws Exception {
        assertEquals(2, command(new SemanticEvaluate(main())).execute("--exper=guard", option,
                "--".equals(option) ? "--json" : "--json=false"));
        assertTrue(errors.toString().contains("Usage:"));
        assertEquals("", printer.getOutput());
        assertTrue(actionFiles().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "3.22.0,false", "4.9.9,true", "4.21.0,false", "4.22.1,true", "4.22.0.redhat-00001,false" })
    void rejectsOlderRuntimesWithoutPublishingRequest(String version, boolean evaluate) throws Exception {
        writeVersion(version);
        SemanticActionCommand action = evaluate ? new SemanticEvaluate(main()) : new CamelSemantic(main());
        String[] args = evaluate
                ? new String[] { Long.toString(pid), "--evaluation=safe", "--json" }
                : new String[] { Long.toString(pid), "--json" };
        assertEquals(3, command(action).execute(args));
        assertError(3, "requires Camel 4.23 or newer");
        assertTrue(errors.toString().contains(version));
        assertTrue(actionFiles().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = { "4.23.0", "4.23.0-SNAPSHOT", "4.23.0.redhat-00001", "4.24.0", "5.0.0", "unknown" })
    void acceptsSupportedOrUnknownRuntimeVersions(String version) throws Exception {
        writeVersion(version);
        assertEquals(0, respond(new CamelSemantic(main()), r -> jsonUnchecked("{\"evaluations\":[]}"), "--json"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "get", "eval", "audit" })
    void mainDispatchesSemanticGroupIncludingDefaultGet(String subcommand) throws Exception {
        AtomicInteger exitCode = new AtomicInteger(-1);
        CamelJBangMain main = new CamelJBangMain() {
            @Override
            public void preExecute(CommandLine command, String[] args) {
                command.setErr(new PrintWriter(errors, true));
            }

            @Override
            public void quit(int code) {
                exitCode.set(code);
            }
        };
        main.withPrinter(printer);
        List<String> args = new ArrayList<>(List.of("semantic"));
        if (!subcommand.isEmpty()) {
            args.add(subcommand);
        }
        args.add(Long.toString(pid));
        args.add("--json");
        if ("eval".equals(subcommand)) {
            args.add("--evaluation=safe");
        }
        assertEquals(0, respond(() -> {
            main.execute(args.toArray(String[]::new));
            return exitCode.get();
        }, request -> {
            String action = switch (subcommand) {
                case "eval" -> "semantic-evaluate";
                case "audit" -> "semantic-audit";
                default -> "semantic-metadata";
            };
            assertEquals(action, request.get("action"));
            return jsonUnchecked("audit".equals(subcommand)
                    ? "{\"audit\":{\"reader\":\"memory\"},\"records\":[]}"
                    : "{\"status\":\"success\",\"evaluations\":[]}");
        }));
        if ("audit".equals(subcommand)) {
            assertEquals("memory", json(printer.getOutput()).getMap("audit").get("reader"));
        } else {
            assertEquals("success", json(printer.getOutput()).get("status"));
        }
    }

    @Test
    void auditTableIncludesEventIdentityAndKeepsDecisionsSeparateFromEvaluationStatus() throws Exception {
        assertEquals(0, respond(new SemanticAudit(main()), request -> {
            assertEquals("semantic-audit", request.get("action"));
            assertEquals(50L, request.getLong("limit"));
            assertEquals(2, request.size());
            return jsonUnchecked("""
                    {"audit":{"reader":"memory","enabled":true,"dropped":2},
                     "records":[{"eventId":"decision-1","timestamp":"2026-10-10T08:00:00Z",
                       "category":"decision","action":"block","operation":"tools/call","target":"support-request",
                       "namespace":"test","reasonCode":"policy_denied","correlationId":"request-123"},
                       {"eventId":"evaluation-1","timestamp":"2026-10-10T07:59:59Z","category":"evaluation",
                        "expert":"guard","status":"success","result":{"value":false}}],
                     "nextCursor":"opaque:cursor","evicted":7,"cursorExpired":false}
                    """);
        }));
        for (String value : List.of("EVENT ID", "TIMESTAMP", "ACTION", "STATUS", "decision-1", "evaluation-1",
                "block", "success", "guard", "tools/call", "support-request", "policy_denied", "request-123",
                "Next cursor: opaque:cursor", "Evicted: 7", "\"dropped\":2")) {
            assertTrue(printer.getOutput().contains(value), printer.getOutput());
        }
        assertFalse(printer.getOutput().contains("allow"));
        assertEquals("", errors.toString());
    }

    @Test
    void auditFiltersKeepTheDispatchActionAndPreserveTheJsonPage() throws Exception {
        JsonObject page = json("""
                {"audit":{"reader":"custom"},"records":[],"nextCursor":"next-page","evicted":3,"cursorExpired":false}
                """);
        assertEquals(0, respond(new SemanticAudit(main()), request -> {
            assertEquals("semantic-audit", request.get("action"));
            assertEquals("block", request.get("auditAction"));
            assertEquals("decision", request.get("category"));
            assertEquals("guard", request.get("expert"));
            assertEquals("route-1", request.get("routeId"));
            assertEquals("test", request.get("namespace"));
            assertEquals("correlation=123", request.get("correlationId"));
            assertEquals("2026-10-10T08:00:00Z", request.get("since"));
            assertEquals("opaque=cursor", request.get("cursor"));
            assertEquals(20L, request.getLong("limit"));
            return page;
        }, "--category=decision", "--action=block", "--expert=guard", "--route-id=route-1", "--namespace=test",
                "--correlation-id=correlation=123", "--since=2026-10-10T10:00:00+02:00", "--cursor=opaque=cursor",
                "--limit=20", "--json"));
        assertEquals(page, json(printer.getOutput()));
        assertEquals("", errors.toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void auditEventIncludesHistoricalEvidenceAndUnavailableReferences(boolean jsonOutput) throws Exception {
        JsonObject response = json("""
                {"audit":{"reader":"memory","enabled":false},
                 "record":{"eventId":"decision-1","category":"decision","action":"block",
                           "evidence":["evaluation-1","evicted-1"]},
                 "evidence":[{"eventId":"evaluation-1","provider":"safety-provider","model":"guard-v1","revision":"r1",
                              "result":{"value":false},"semantics":{"meaning":"Injection detected"}},
                             {"eventId":"evicted-1","unavailable":true}]}
                """);
        assertEquals(0, respond(new SemanticAudit(main()), request -> {
            assertEquals("semantic-audit", request.get("action"));
            assertEquals("decision-1", request.get("eventId"));
            assertEquals(2, request.size());
            return response;
        }, "--event-id=decision-1", "--json=" + jsonOutput));
        if (jsonOutput) {
            assertEquals(response, json(printer.getOutput()));
        } else {
            for (String value : List.of("decision-1", "Evidence:", "evaluation-1", "safety-provider", "guard-v1",
                    "Injection detected", "evicted-1", "\"unavailable\": true")) {
                assertTrue(printer.getOutput().contains(value), printer.getOutput());
            }
        }
        assertEquals("", errors.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 200 })
    void auditAcceptsPageLimitBoundsAndEmptyHistory(int limit) throws Exception {
        assertEquals(0, respond(new SemanticAudit(main()), request -> {
            assertEquals((long) limit, request.getLong("limit"));
            return jsonUnchecked("""
                    {"audit":{"enabled":false,"reader":"memory"},"records":[],"evicted":0,"cursorExpired":false}
                    """);
        }, "--limit=" + limit));
        assertTrue(printer.getOutput().contains("No retained audit records match this query."));
        assertEquals("", errors.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "--limit=0", "--limit=201", "--limit=not-a-number", "--since=yesterday", "--expert=",
            "--category=", "--action=", "--namespace=", "--correlation-id=", "--route-id=", "--cursor=", "--event-id=" })
    void auditRejectsInvalidArgumentsBeforeSendingARequest(String option) throws Exception {
        assertEquals(2, command(new SemanticAudit(main())).execute(Long.toString(pid), option, "--json"));
        assertError(2, "");
        assertTrue(actionFiles().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "--expert,257", "--cursor,513", "--event-id,257" })
    void auditRejectsOversizedIdentifiersBeforeSendingARequest(String option, int length) throws Exception {
        assertEquals(2, command(new SemanticAudit(main())).execute(Long.toString(pid),
                option + "=" + "x".repeat(length), "--json"));
        assertError(2, option);
        assertTrue(actionFiles().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "--expert=guard", "--action=block", "--since=2026-10-10T08:00:00Z", "--cursor=older",
            "--limit=50" })
    void auditEventLookupRejectsIgnoredQueryOptions(String option) throws Exception {
        assertEquals(2, command(new SemanticAudit(main())).execute(Long.toString(pid), "--event-id=decision-1",
                option, "--json"));
        assertError(2, "cannot be combined");
        assertTrue(actionFiles().isEmpty());
    }

    @Test
    void auditQueryFailureHasNonzeroExitAndKeepsTheStableErrorCode() throws Exception {
        assertEquals(1, respond(new SemanticAudit(main()), request -> jsonUnchecked("""
                {"audit":{"reader":"remote"},"error":"audit_query_failed"}
                """), "--json"));
        assertError(1, "Audit query failed");
        assertEquals("audit_query_failed", json(printer.getOutput()).get("error"));
        assertEquals("remote", json(printer.getOutput()).getMap("audit").get("reader"));
    }

    @Test
    void auditUnavailableEventRetainsItsResponseWithNotFoundExitCode() throws Exception {
        assertEquals(3, respond(new SemanticAudit(main()), request -> jsonUnchecked("""
                {"audit":{"reader":"memory"},"record":null,"evidence":[]}
                """), "--event-id=missing-event", "--json"));
        assertError(3, "missing-event");
        assertTrue(json(printer.getOutput()).containsKey("record"));
        assertTrue(json(printer.getOutput()).getCollection("evidence").isEmpty());
    }

    @Test
    void auditExpiredCursorIsAnExplicitFailureWithRetentionDetails() throws Exception {
        assertEquals(1, respond(new SemanticAudit(main()), request -> jsonUnchecked("""
                {"audit":{"reader":"memory"},"records":[],"cursorExpired":true,"evicted":9}
                """), "--cursor=old-page", "--json"));
        assertError(1, "cursor expired");
        assertEquals(true, json(printer.getOutput()).get("cursorExpired"));
        assertEquals(9L, json(printer.getOutput()).getLong("evicted"));
    }

    private void writeVersion(String version) throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(pid + "-status.json"),
                "{\"context\":{\"name\":\"semantic-test\",\"phase\":4,\"version\":\"" + version + "\"}}");
    }

    private CamelJBangMain main() {
        return new CamelJBangMain().withPrinter(printer);
    }

    private CommandLine command(SemanticActionCommand command) {
        return new CommandLine(command).setErr(new PrintWriter(errors, true))
                .setParameterExceptionHandler(new MissingPluginParameterExceptionHandler());
    }

    private int respond(SemanticActionCommand command, Function<JsonObject, JsonObject> response, String... options)
            throws Exception {
        List<String> args = new ArrayList<>(List.of(options));
        args.add(Long.toString(pid));
        return respond(() -> command(command).execute(args.toArray(String[]::new)), response);
    }

    private int respond(IntSupplier call, Function<JsonObject, JsonObject> response) throws Exception {
        var responder = CompletableFuture.runAsync(() -> {
            try {
                await().atMost(5, TimeUnit.SECONDS).until(() -> !actionFiles().isEmpty());
                Path request = actionFiles().get(0);
                await().atMost(5, TimeUnit.SECONDS).until(() -> Files.size(request) > 0);
                reply(request, response.apply(json(Files.readString(request))));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
        int result = call.getAsInt();
        responder.get(5, TimeUnit.SECONDS);
        assertTrue(actionFiles().isEmpty());
        return result;
    }

    private static void reply(Path request, JsonObject response) throws Exception {
        Path output = request.resolveSibling(request.getFileName().toString().replace("-action-", "-output-"));
        Files.writeString(output, response.toJson());
    }

    private List<Path> actionFiles() throws Exception {
        try (var files = Files.list(CommandLineHelper.getCamelDir())) {
            return files.filter(path -> path.getFileName().toString().startsWith(pid + "-action-")).toList();
        }
    }

    private void assertError(int code, String message) {
        JsonObject result = jsonUnchecked(printer.getOutput());
        assertEquals("error", result.get("status"));
        assertEquals(code, result.getInteger("code"));
        assertTrue(result.getString("message").contains(message), result.toJson());
        assertFalse(errors.toString().isBlank());
    }

    private static JsonObject json(String value) throws Exception {
        JsonArray documents = Jsoner.deserializeMany(new StringReader(value));
        assertEquals(1, documents.size(), "Expected exactly one JSON document");
        return documents.getMap(0);
    }

    private static JsonObject jsonUnchecked(String value) {
        try {
            return json(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
