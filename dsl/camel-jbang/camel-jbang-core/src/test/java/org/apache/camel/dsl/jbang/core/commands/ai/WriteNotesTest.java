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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The notes of a write for what works but is probably not what was asked: a to: that copies the files a file consumer
 * moves to .camel, a to: that appends to a file with nothing between the messages, a request-reply over JMS whose reply
 * replaces the message, and a literal over a {{key}} placeholder that leaves the key unused.
 */
class WriteNotesTest {

    /** connect-file-processing step 1 of the local-model benchmark, as r24 and r25 wrote it 4 times. */
    private static final String COPY = """
            - route:
                id: invoices
                from:
                  uri: file
                  parameters:
                    directoryName: inbox
                    sortBy: "file:name"
                    include: ".*\\\\.json"
                  steps:
                    - log:
                        message: "Received ${file:name}"
                    - to:
                        uri: file
                        parameters:
                          directoryName: done
            """;

    @Test
    void aToFileAfterAConsumerThatMovesTheFiles() {
        assertThat(WriteNotes.fileCopyNotes(COPY)).singleElement().isEqualTo(
                "Line 12: this to: writes a copy of each file to done, while the file consumer still moves the file"
                                                                             + " itself to inbox/.camel after the route (its default). To move the file to done"
                                                                             + " instead, remove this to: and add move: done under parameters: of the from:"
                                                                             + " (the file then goes to inbox/done). A move value that starts with a dot, such"
                                                                             + " as .done, is a hidden folder.");
    }

    @Test
    void theUriFormGetsTheUriToWrite() {
        String uri = """
                - route:
                    from:
                      uri: "file:inbox?include=.*json"
                      steps:
                        - toD: file:done
                """;
        assertThat(WriteNotes.fileCopyNotes(uri)).singleElement().asString()
                .startsWith("Line 5: this to: writes a copy of each file to done")
                .contains("remove this to: and write the uri of the from: as file:inbox?include=.*json&move=done (the"
                          + " file then goes to inbox/done)");
        // a folder inside the one the consumer reads is named as move names it
        assertThat(WriteNotes.fileCopyNotes(uri.replace("toD: file:done", "to: file:inbox/done"))).singleElement()
                .asString().contains("as file:inbox?include=.*json&move=done (the file then goes to inbox/done)");
    }

    @Test
    void aConsumerThatDecidesWhereTheFileGoesHasNoNote() {
        for (String option : new String[] {
                "noop: true", "move: done", "move: .done", "delete: true", "moveFailed: failed",
                "preMove: inprogress" }) {
            assertThat(WriteNotes.fileCopyNotes(COPY.replace("sortBy: \"file:name\"", option))).as(option).isEmpty();
        }
        assertThat(WriteNotes.fileCopyNotes(COPY.replace("uri: file\n      parameters:\n        directoryName: inbox",
                "uri: file:inbox?noop=true\n      parameters:"))).isEmpty();
    }

    @Test
    void aCopyWrittenForAReasonHasNoNote() {
        // a transformed file
        assertThat(WriteNotes.fileCopyNotes(COPY.replace("""
                        - log:
                            message: "Received ${file:name}"
                """, """
                        - unmarshal:
                            json: {}
                        - marshal:
                            json: {}
                """))).isEmpty();
        // a copy in another charset, or under another name (as the file component's doc shows)
        assertThat(WriteNotes.fileCopyNotes(COPY.replace("directoryName: done", "directoryName: done\n"
                                                                                + "              charset: iso-8859-1")))
                .isEmpty();
        assertThat(WriteNotes.fileCopyNotes(COPY.replace("directoryName: done", "directoryName: done\n"
                                                                                + "              fileName: copy.json")))
                .isEmpty();
        assertThat(WriteNotes.fileCopyNotes(COPY.replace("""
                        - log:
                            message: "Received ${file:name}"
                """, """
                        - setHeader:
                            name: CamelFileName
                            simple: "${date:now:yyyyMMdd}-${file:name}"
                """))).isEmpty();
        // a copy kept next to another call, and a copy to the folder it reads
        assertThat(WriteNotes.fileCopyNotes(COPY + "        - to: bean:archive\n")).isEmpty();
        assertThat(WriteNotes.fileCopyNotes(COPY.replace("directoryName: done", "directoryName: inbox"))).isEmpty();
        // not a file consumer
        assertThat(WriteNotes.fileCopyNotes(COPY.replace("uri: file\n      parameters:", "uri: direct:in\n      parameters:")))
                .isEmpty();
    }

    /**
     * route-batch-file step 2 of the local-model benchmark, as r32 wrote it: each line that cannot be read is appended
     * to rejects/<file name>, with nothing between the lines.
     */
    private static final String APPEND = """
            - route:
                id: settlement
                from:
                  uri: file
                  parameters:
                    directoryName: inbox
                    move: done
                  steps:
                    - split:
                        expression:
                          tokenize:
                            token: "\\n"
                            skipFirst: true
                        streaming: true
                        steps:
                          - doTry:
                              steps:
                                - unmarshal:
                                    csv:
                                      header: "paymentId,orderId,customer,amount,currency"
                                      useMaps: true
                              doCatch:
                                - exception:
                                    - java.lang.Exception
                                  steps:
                                    - setHeader:
                                        name: CamelFileName
                                        expression:
                                          simple: "rejects/${file:name}"
                                    - setBody:
                                        expression:
                                          simple: "${variable.line}"
                                    - to:
                                        uri: file
                                        parameters:
                                          directoryName: .
                                          fileExist: Append
            """;

    /** The to: of {@link #APPEND} replaced by the step given, which is written from column 0. */
    private static String appendWith(String step) {
        return APPEND.substring(0, APPEND.indexOf(" ".repeat(24) + "- to:\n")) + step.indent(24);
    }

    /** {@link #APPEND} with appendChars under the parameters: of its to:, its value written as given. */
    private static String withAppendChars(String value) {
        return APPEND + " ".repeat(30) + "appendChars: " + value + "\n";
    }

    @Test
    void anAppendWithNothingBetweenTheMessages() {
        assertThat(WriteNotes.fileAppendNotes(APPEND)).singleElement().isEqualTo(
                "Line 33: fileExist Append adds each message right after the one before, with nothing between them. To"
                                                                                 + " write one message per line, add appendChars: \"\\\\n\" under parameters: of"
                                                                                 + " this to:, which writes a line break after each message.");
        // the value in another case, as Camel reads it
        assertThat(WriteNotes.fileAppendNotes(APPEND.replace("fileExist: Append", "fileExist: append"))).hasSize(1);
    }

    @Test
    void anAppendInTheUriGetsTheUriToWrite() {
        // as r32n-3 wrote it: %5Cn is right whatever the quotes of the uri
        String uri = appendWith("- to: \"file:rejects?fileName=${header.fileName}&fileExist=Append\"");
        assertThat(WriteNotes.fileAppendNotes(uri)).singleElement().isEqualTo(
                "Line 33: fileExist Append adds each message right after the one before, with nothing between them. To"
                                                                              + " write one message per line, write the uri of this to: as"
                                                                              + " file:rejects?fileName=${header.fileName}&fileExist=Append&appendChars=%5Cn,"
                                                                              + " which writes a line break after each message. %5Cn is \\n as a uri writes"
                                                                              + " it.");
        // the option names in another case, as Camel reads them
        assertThat(WriteNotes.fileAppendNotes(uri.replace("fileExist=Append", "fileexist=APPEND"))).hasSize(1);
    }

    @Test
    void aLineBreakAfterEachMessageHasNoNote() {
        // appendChars as the file component reads it: the two characters \n, in each YAML form, and as a uri writes it
        assertThat(WriteNotes.fileAppendNotes(withAppendChars("\"\\\\n\""))).isEmpty();
        assertThat(WriteNotes.fileAppendNotes(withAppendChars("\\n"))).isEmpty();
        assertThat(WriteNotes.fileAppendNotes(withAppendChars("\"%5Cn\""))).isEmpty();
        assertThat(WriteNotes.fileAppendNotes(appendWith("- to: \"file:rejects?fileExist=Append&appendChars=\\\\n\"")))
                .isEmpty();
        assertThat(WriteNotes.fileAppendNotes(appendWith("- to: \"file:rejects?fileExist=Append&appendChars=%5Cn\"")))
                .isEmpty();
        // another separator is the route's choice
        assertThat(WriteNotes.fileAppendNotes(withAppendChars("\";\""))).isEmpty();
        // a body that ends with a line break already: \n in Simple, and a record of csv
        assertThat(WriteNotes.fileAppendNotes(APPEND.replace("\"${variable.line}\"", "\"${variable.line}\\\\n\"")))
                .isEmpty();
        assertThat(WriteNotes.fileAppendNotes(APPEND.replace("""
                - setBody:
                    expression:
                      simple: "${variable.line}"
                """.indent(24), """
                - marshal:
                    csv: {}
                """.indent(24)))).isEmpty();
    }

    @Test
    void aLineBreakOfTheYamlAddsNothing() {
        // the endpoint uri drops it, as the runtime logged for r32n-6: appendChars=&fileExist=Append
        assertThat(WriteNotes.fileAppendNotes(withAppendChars("\"\\n\""))).singleElement().isEqualTo(
                "Line 33: this appendChars is a line break in the YAML, and the endpoint uri drops line breaks, so"
                                                                                                     + " fileExist Append adds each message right after the one before, with nothing"
                                                                                                     + " between them. To write one message per line, write appendChars: \"\\\\n\""
                                                                                                     + " under parameters: of this to:, which writes a line break after each message.");
        // Simple trims a line break of the YAML at the end of the body, as r32n-7 wrote it: the note stays
        assertThat(WriteNotes.fileAppendNotes(APPEND.replace("\"${variable.line}\"", "\"${variable.line}\\n\"")))
                .singleElement().asString().startsWith("Line 33: fileExist Append adds each message");
    }

    @Test
    void aToDWithAnExpressionGetsTheValueItKeeps() {
        // as r32n-6 wrote it: the toD reads its uri as Simple, which turns \n into a line break the uri drops
        String toD = appendWith("""
                - toD:
                    uri: "file:rejects/${header.fileName}"
                    parameters:
                      fileExist: Append
                      appendChars: "\\n"
                """);
        String fix = "To write one message per line, write appendChars: \"%5Cn\" under parameters: of this toD:, which"
                     + " writes a line break after each message. In a toD with ${...}, write \\n as %5Cn: the toD turns \\n"
                     + " into a line break, which the uri drops.";
        assertThat(WriteNotes.fileAppendNotes(toD)).singleElement().isEqualTo(
                "Line 33: this appendChars is a line break in the YAML, and the endpoint uri drops line breaks, so"
                                                                              + " fileExist Append adds each message right after the one before, with nothing"
                                                                              + " between them. " + fix);
        // the two characters \n become a line break in the toD too
        assertThat(WriteNotes.fileAppendNotes(toD.replace("appendChars: \"\\n\"", "appendChars: \"\\\\n\"")))
                .singleElement().isEqualTo("Line 33: fileExist Append adds each message right after the one before, with"
                                           + " nothing between them. " + fix);
        // ${...} in the directoryName of the parameters is in the uri of the toD too
        assertThat(WriteNotes.fileAppendNotes(toD.replace("uri: \"file:rejects/${header.fileName}\"", "uri: file")
                .replace("fileExist: Append\n",
                        "directoryName: \"rejects/${header.dir}\"\n" + " ".repeat(30) + "fileExist: Append\n")
                .replace("appendChars: \"\\n\"", "appendChars: \"\\\\n\""))).singleElement().isEqualTo(
                        "Line 33: fileExist Append adds each message right after the one before, with nothing between"
                                                                                                       + " them. " + fix);
        // %5Cn is kept, and a toD without ${...} reads its uri as it is
        assertThat(WriteNotes.fileAppendNotes(toD.replace("appendChars: \"\\n\"", "appendChars: \"%5Cn\""))).isEmpty();
        assertThat(WriteNotes.fileAppendNotes(toD.replace("rejects/${header.fileName}", "rejects")
                .replace("appendChars: \"\\n\"", "appendChars: \"\\\\n\""))).isEmpty();
        // as r32n-7 wrote it: no appendChars, in the uri of a toD
        assertThat(WriteNotes.fileAppendNotes(appendWith("- toD: \"file:${header.CamelFileName}?fileExist=Append\"")))
                .singleElement().isEqualTo(
                        "Line 33: fileExist Append adds each message right after the one before, with nothing between"
                                           + " them. To write one message per line, write the uri of this toD: as"
                                           + " file:${header.CamelFileName}?fileExist=Append&appendChars=%5Cn, which writes"
                                           + " a line break after each message. In a toD with ${...}, write \\n as %5Cn:"
                                           + " the toD turns \\n into a line break, which the uri drops.");
    }

    @Test
    void aWriteThatDoesNotAppendHasNoNote() {
        assertThat(WriteNotes.fileAppendNotes(APPEND.replace("fileExist: Append", "fileExist: Override"))).isEmpty();
        assertThat(WriteNotes.fileAppendNotes(APPEND.replace("fileExist: Append", "fileExist: \"{{rejects.exist}}\"")))
                .isEmpty();
        // a file consumer writes nothing
        assertThat(WriteNotes.fileAppendNotes("""
                - route:
                    from:
                      uri: "file:rejects?fileExist=Append"
                      steps:
                        - log: "${body}"
                """)).isEmpty();
        // the remote file components have no appendChars
        assertThat(WriteNotes.fileAppendNotes(appendWith("- to: \"sftp:files.example.com/rejects?fileExist=Append\"")))
                .isEmpty();
    }

    /**
     * connect-service-jms-request-reply step 2 of the local-model benchmark ("keep the order as the body"), r23 to r26.
     */
    private static final String REQUEST_REPLY = """
            - route:
                id: reserve-order
                from:
                  uri: jms
                  parameters:
                    destinationType: queue
                    destinationName: orders
                  steps:
                    - log:
                        message: "Reserving ${header.orderId} for customer ${header.customer}"
                    - to:
                        uri: jms
                        parameters:
                          destinationType: queue
                          destinationName: stock
                        pattern: InOut
                    - log:
                        message: "Stock service answered for ${header.orderId}: ${body}"
            """;

    @Test
    void aRequestReplyWhoseReplyReplacesTheMessage() {
        assertThat(WriteNotes.requestReplyNotes(REQUEST_REPLY)).singleElement().isEqualTo(
                "Line 11: after this request-reply (InOut) the body is the reply from stock, and the message that was"
                                                                                          + " sent is gone. To keep the message as the body, add variableReceive:"
                                                                                          + " stockReply to this to: and read the reply as ${variable.stockReply}.");
    }

    @Test
    void theWaysToSendInOut() {
        // exchangePattern on the endpoint, under parameters: or in the uri, and the to: as a uri alone
        assertThat(WriteNotes.requestReplyNotes(REQUEST_REPLY.replace("            pattern: InOut",
                "              exchangePattern: InOut"))).singleElement().asString()
                .startsWith("Line 11: after this request-reply (InOut) the body is the reply from stock");
        String uri = REQUEST_REPLY.replace("""
                            uri: jms
                            parameters:
                              destinationType: queue
                              destinationName: stock
                            pattern: InOut
                """, """
                            uri: "activemq:queue:stock?exchangePattern=InOut"
                """);
        assertThat(WriteNotes.requestReplyNotes(uri)).singleElement().asString()
                .startsWith("Line 11: after this request-reply (InOut) the body is the reply from stock");
        assertThat(WriteNotes.requestReplyNotes(uri.replace("        - to:\n            uri: ", "        - to: ")))
                .singleElement().asString().contains("add variableReceive: stockReply to this to:");
        // a setExchangePattern before it, as r23n-8 wrote it, and the to: inside a filter
        String exchange = REQUEST_REPLY.replace("""
                        - to:
                            uri: jms
                            parameters:
                              destinationType: queue
                              destinationName: stock
                            pattern: InOut
                """, """
                        - setExchangePattern:
                            pattern: InOut
                        - filter:
                            expression:
                              simple:
                                expression: "${header.orderId} != null"
                            steps:
                              - to: jms:queue:stock
                """);
        assertThat(WriteNotes.requestReplyNotes(exchange)).singleElement().asString()
                .startsWith("Line 18: after this request-reply (InOut) the body is the reply from stock");
    }

    @Test
    void aRequestReplyThatKeepsTheMessageHasNoNote() {
        // the reply in a variable, as the jms sample shows
        assertThat(WriteNotes.requestReplyNotes(REQUEST_REPLY.replace("pattern: InOut",
                "pattern: InOut\n            variableReceive: stockReply"))).isEmpty();
        // the body set after the reply: the route has chosen what the body is
        assertThat(WriteNotes.requestReplyNotes(REQUEST_REPLY + """
                        - setBody:
                            expression:
                              simple:
                                expression: "${variable.order}"
                """)).isEmpty();
        // sent InOnly, InOut on another component, and InOnly on the to: after a setExchangePattern
        assertThat(WriteNotes.requestReplyNotes(REQUEST_REPLY.replace("pattern: InOut", "pattern: InOnly"))).isEmpty();
        assertThat(WriteNotes.requestReplyNotes(REQUEST_REPLY.replace("uri: jms\n            parameters:",
                "uri: direct:stock\n            parameters:"))).isEmpty();
        assertThat(WriteNotes.requestReplyNotes(REQUEST_REPLY.replace("pattern: InOut", "pattern: InOnly").replace("""
                        - to:
                """, """
                        - setExchangePattern: InOut
                        - to:
                """))).isEmpty();
    }

    /** The request-reply of the benchmark with one more option of the endpoint under parameters:. */
    private static String requestReplyWith(String option) {
        return REQUEST_REPLY.replace("destinationName: stock", "destinationName: stock\n              " + option);
    }

    @Test
    void thePatternIsTheOneTheRuntimeSendsWith() {
        // review of #29147: exchangePattern of the endpoint wins over pattern: of the to: (as in SendProcessor)
        assertThat(WriteNotes.requestReplyNotes(requestReplyWith("exchangePattern: InOnly"))).isEmpty();
        String uri = """
                - route:
                    from:
                      uri: jms:queue:orders
                      steps:
                        - to:
                            uri: "jms:queue:stock?exchangePattern=InOnly"
                            pattern: InOut
                """;
        assertThat(WriteNotes.requestReplyNotes(uri)).isEmpty();
        assertThat(WriteNotes.requestReplyNotes("""
                - route:
                    from:
                      uri: jms:queue:orders
                      steps:
                        - to:
                            uri: "jms:queue:stock?exchangePattern=InOut"
                            pattern: InOnly
                """)).singleElement().asString()
                .startsWith("Line 5: after this request-reply (InOut) the body is the reply from stock");
        // disableReplyTo sends InOnly whatever the pattern: the body is kept
        assertThat(WriteNotes.requestReplyNotes(uri.replace("exchangePattern=InOnly", "disableReplyTo=true"))).isEmpty();
        assertThat(WriteNotes.requestReplyNotes(requestReplyWith("disableReplyTo: true"))).isEmpty();
        assertThat(WriteNotes.requestReplyNotes(requestReplyWith("disableReplyTo: false"))).hasSize(1);
    }

    @Test
    void aBodySetLaterOnTheMainPathHidesTheNote() {
        // review of #29147: a setBody after the filter that holds the to: is on the main path
        String filter = """
                - route:
                    from:
                      uri: jms:queue:orders
                      steps:
                        - filter:
                            expression:
                              simple:
                                expression: "${header.orderId} != null"
                            steps:
                              - to:
                                  uri: jms:queue:stock
                                  pattern: InOut
                """;
        String setBody = """
                        - setBody:
                            expression:
                              simple:
                                expression: "${variable.order}"
                """;
        assertThat(WriteNotes.requestReplyNotes(filter)).singleElement().asString().startsWith("Line 10: after this");
        assertThat(WriteNotes.requestReplyNotes(filter + setBody)).isEmpty();
        // a doFinally runs on the main path too: after the doTry, and after the steps of its own doTry
        assertThat(WriteNotes.requestReplyNotes("""
                - route:
                    from:
                      uri: jms:queue:orders
                      steps:
                        - to:
                            uri: jms:queue:stock
                            pattern: InOut
                        - doTry:
                            steps:
                              - log: "${body}"
                            doFinally:
                              steps:
                                - setBody:
                                    expression:
                                      simple:
                                        expression: "${variable.order}"
                """)).isEmpty();
        assertThat(WriteNotes.requestReplyNotes("""
                - route:
                    from:
                      uri: jms:queue:orders
                      steps:
                        - doTry:
                            steps:
                              - to:
                                  uri: jms:queue:stock
                                  pattern: InOut
                            doFinally:
                              steps:
                                - setBody:
                                    expression:
                                      simple:
                                        expression: "${variable.order}"
                """)).isEmpty();
        // a setBody in one branch of a choice after the to: hides the note, as the note only shows when it is clearly
        // right
        assertThat(WriteNotes.requestReplyNotes("""
                - route:
                    from:
                      uri: jms:queue:orders
                      steps:
                        - to:
                            uri: jms:queue:stock
                            pattern: InOut
                        - choice:
                            when:
                              - expression:
                                  simple:
                                    expression: "${header.keep}"
                                steps:
                                  - setBody:
                                      expression:
                                        simple:
                                          expression: "${variable.order}"
                            otherwise:
                              steps:
                                - log: "${body}"
                """)).isEmpty();
    }

    @Test
    void aBodySetOnlyOnAFailureKeepsTheNote() {
        // review of #29147: a setBody only in a doCatch runs on a failure, and the body of the main path is the reply
        assertThat(WriteNotes.requestReplyNotes("""
                - route:
                    from:
                      uri: jms:queue:orders
                      steps:
                        - to:
                            uri: jms:queue:stock
                            pattern: InOut
                        - doTry:
                            steps:
                              - log: "${body}"
                            doCatch:
                              - exception:
                                  - java.lang.Exception
                                steps:
                                  - setBody:
                                      expression:
                                        simple:
                                          expression: "${variable.order}"
                """)).singleElement().asString().startsWith("Line 5: after this request-reply");
        // the fallback of a circuit breaker runs on a failure too
        assertThat(WriteNotes.requestReplyNotes("""
                - route:
                    from:
                      uri: jms:queue:orders
                      steps:
                        - to:
                            uri: jms:queue:stock
                            pattern: InOut
                        - circuitBreaker:
                            steps:
                              - log: "${body}"
                            onFallback:
                              steps:
                                - setBody:
                                    expression:
                                      simple:
                                        expression: "${variable.order}"
                """)).singleElement().asString().startsWith("Line 5: after this request-reply");
    }

    @Test
    void aLiteralOverAPlaceholderLeavesThePropertyUnused(@TempDir Path dir) throws Exception {
        // run-nightly-report step 4 of the local-model benchmark: asked to change the schedule, 4 runs wrote the cron
        // in the route
        String before = """
                - route:
                    id: nightly-report
                    from:
                      uri: cron
                      parameters:
                        name: report
                        schedule: "{{report.schedule}}"
                      steps:
                        - log:
                            message: "Inventory report: {{inventory.tshirt}} T-shirts and {{inventory.mug}} mugs"
                """;
        String after = before.replace("\"{{report.schedule}}\"", "\"0 0 2 * * ?\"");
        Files.writeString(dir.resolve("nightly-report.camel.yaml"), after);
        Files.writeString(dir.resolve("application.properties"),
                "report.schedule=0/10 * * * * ?\ninventory.tshirt=120\ninventory.mug=45\n");

        assertThat(WriteNotes.unusedPropertyNotes(dir, "nightly-report.camel.yaml", before, after)).singleElement()
                .isEqualTo("report.schedule in application.properties is no longer used by any route. To change the"
                           + " schedule, change report.schedule in application.properties and keep {{report.schedule}}"
                           + " in the route.");
        // a uri option, and a placeholder inside a text
        String uri = "- from:\n    uri: \"cron:report?schedule={{report.schedule}}\"\n    steps:\n      - log: hi\n";
        assertThat(WriteNotes.unusedPropertyNotes(dir, "nightly-report.camel.yaml", uri,
                uri.replace("{{report.schedule}}", "0 0 2 * * ?"))).singleElement().asString()
                .contains("To change the schedule, change report.schedule");
        // the route file has what the write wrote
        String mug = before.replace("{{inventory.mug}}", "45");
        Files.writeString(dir.resolve("nightly-report.camel.yaml"), mug);
        assertThat(WriteNotes.unusedPropertyNotes(dir, "nightly-report.camel.yaml", before, mug)).singleElement().asString()
                .startsWith("inventory.mug in application.properties is no longer used by any route. To change the"
                            + " value, change inventory.mug");
    }

    @Test
    void whatToDoInsteadNamesTheLineAndTheValue(@TempDir Path dir) throws Exception {
        // r26 run-nightly-report step 4: the note was repeated in the model's answer, and the step ended
        String before = "- route:\n    from:\n      uri: cron\n      parameters:\n        name: report\n"
                        + "        schedule: \"{{report.schedule}}\"\n      steps:\n        - log: hi\n";
        Files.writeString(dir.resolve("application.properties"), "report.schedule=0/10 * * * * ?\n");
        assertThat(WriteNotes.unusedProperties(dir, "a.camel.yaml", before, before.replace("{{report.schedule}}",
                "0 0 2 * * ?"))).singleElement().extracting(WriteNotes.UnusedProperty::instruction).isEqualTo(
                        "Use {{report.schedule}} again on line 6, and set report.schedule=0 0 2 * * ? in"
                                                                                                              + " application.properties: the property is now unused, as line 6 has"
                                                                                                              + " its own value.");
        // the line changed elsewhere too: the value is not told
        String other = before.replace("schedule: \"{{report.schedule}}\"", "schedule: \"0 0 2 * * ?\" # nightly");
        assertThat(WriteNotes.unusedProperties(dir, "a.camel.yaml", before, other)).singleElement()
                .extracting(WriteNotes.UnusedProperty::instruction).asString()
                .startsWith("Use {{report.schedule}} again on line 6, and set the new value as report.schedule in"
                            + " application.properties");
    }

    @Test
    void twoKeysEachGetTheirOwnLineAndValue(@TempDir Path dir) throws Exception {
        // review of #29147: two placeholders with the same text before them, replaced in one write; the second key got
        // the line and the value of the first
        String before = """
                - route:
                    id: nightly-report
                    from:
                      uri: cron
                      parameters:
                        name: report
                        schedule: "{{report.schedule}}"
                      steps:
                        - log: report
                - route:
                    id: nightly-cleanup
                    from:
                      uri: cron
                      parameters:
                        name: cleanup
                        schedule: "{{cleanup.schedule}}"
                      steps:
                        - log: cleanup
                """;
        String after = before.replace("{{report.schedule}}", "0 0 2 * * ?").replace("{{cleanup.schedule}}", "0 0 3 * * ?");
        Files.writeString(dir.resolve("application.properties"),
                "report.schedule=0/10 * * * * ?\ncleanup.schedule=0/20 * * * * ?\n");
        String report = "Use {{report.schedule}} again on line 7, and set report.schedule=0 0 2 * * ? in"
                        + " application.properties: the property is now unused, as line 7 has its own value.";
        String cleanup = "Use {{cleanup.schedule}} again on line 16, and set cleanup.schedule=0 0 3 * * ? in"
                         + " application.properties: the property is now unused, as line 16 has its own value.";
        assertThat(WriteNotes.unusedProperties(dir, "nightly.camel.yaml", before, after))
                .extracting(WriteNotes.UnusedProperty::instruction).containsExactly(report, cleanup);
    }

    @Test
    void aKeyThatIsStillUsedHasNoNote(@TempDir Path dir) throws Exception {
        String before = "- from:\n    uri: cron:report\n    parameters:\n      schedule: \"{{report.schedule}}\"\n"
                        + "    steps:\n      - log: hi\n";
        String after = before.replace("\"{{report.schedule}}\"", "\"0 0 2 * * ?\"");
        Files.writeString(dir.resolve("application.properties"), "report.schedule=0/10 * * * * ?\n");
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before, after)).hasSize(1);

        // by another route
        Files.writeString(dir.resolve("other.camel.yaml"), before);
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before, after)).isEmpty();
        Files.delete(dir.resolve("other.camel.yaml"));
        // by Java, by another property
        Files.writeString(dir.resolve("Report.java"), "@PropertyInject(\"report.schedule\") String schedule;\n");
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before, after)).isEmpty();
        Files.delete(dir.resolve("Report.java"));
        Files.writeString(dir.resolve("application.properties"),
                "report.schedule=0/10 * * * * ?\ncamel.main.description=every {{report.schedule}}\n");
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before, after)).isEmpty();
        // a key of another name is no use of it
        Files.writeString(dir.resolve("application.properties"),
                "report.schedule=0/10 * * * * ?\nreport.schedule.zone=UTC\n");
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before, after)).hasSize(1);
        // still in the route, or not a key of application.properties
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before, after + "# {{report.schedule}}\n")).isEmpty();
        Files.writeString(dir.resolve("application.properties"), "other=1\n");
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before, after)).isEmpty();
    }

    @Test
    void aKeyTheRuntimeReadsOrARouteRemovedHasNoNote(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("application.properties"), "camel.main.name=nightly\nreport.schedule=0 0 2 * * ?\n");
        String before = "- from:\n    uri: cron:report\n    parameters:\n      schedule: \"{{report.schedule}}\"\n"
                        + "    steps:\n      - log: \"{{camel.main.name}}\"\n";
        // camel.* is read by Camel itself
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before,
                before.replace("\"{{camel.main.name}}\"", "nightly"))).isEmpty();
        // the route removed as a whole: no line of it is left
        assertThat(WriteNotes.unusedPropertyNotes(dir, "a.camel.yaml", before + "- from:\n    uri: timer:tick\n",
                "- from:\n    uri: timer:tick\n")).isEmpty();
        // a properties file is not a route
        assertThat(WriteNotes.unusedPropertyNotes(dir, "other.properties", "a={{report.schedule}}\n", "a=1\n")).isEmpty();
    }
}
