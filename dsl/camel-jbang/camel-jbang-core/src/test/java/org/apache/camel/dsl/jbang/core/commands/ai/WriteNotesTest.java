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
 * moves to .camel, and a literal over a {{key}} placeholder that leaves the key unused.
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
