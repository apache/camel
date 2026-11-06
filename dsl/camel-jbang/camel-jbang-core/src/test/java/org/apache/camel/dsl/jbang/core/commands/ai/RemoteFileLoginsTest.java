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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An sftp or ftp endpoint without a login, while another endpoint of the same server logs in: it logs in as anonymous.
 */
class RemoteFileLoginsTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    /** The from: of connect-sftp-transfer, which logs in with the properties of the project. */
    private static final String FROM = """
            - route:
                id: invoice-batches
                from:
                  uri: sftp
                  parameters:
                    host: "{{sftp.host}}"
                    port: "{{sftp.port}}"
                    directoryName: invoices
                    username: "{{sftp.username}}"
                    password: "{{sftp.password}}"
                    useUserKnownHostsFile: false
                    noop: true
                    moveFailed: failed
                  steps:
                    - log:
                        message: "Downloaded ${file:name}"
                    - setBody:
                        simple: "Received ${file:name}"
            """;

    private static final String PROPERTIES = """
            sftp.host=localhost
            sftp.port=2222
            sftp.username=admin
            sftp.password=admin
            """;

    /** r28n-1 connect-sftp-transfer step 3 [15] (shortened): the receipt uploaded to the same server, as anonymous. */
    @Test
    void aToWithoutALoginToTheServerOfTheFrom() {
        String yaml = FROM + """
                        - to:
                            uri: "sftp://{{sftp.host}}:{{sftp.port}}/receipts"
                            parameters:
                              fileName: "${file:name.noext}.txt"
                """;
        assertThat(RemoteFileLogins.validate(yaml, catalog, null, null)).singleElement().isEqualTo(
                "Line 20: to: sftp://{{sftp.host}}:{{sftp.port}}/receipts has no username, so it logs in as anonymous,"
                                                                                                   + " while the from: on line 4 logs in to the same server: add the same login under the"
                                                                                                   + " parameters: of this to: username: \"{{sftp.username}}\", password:"
                                                                                                   + " \"{{sftp.password}}\", useUserKnownHostsFile: false");
    }

    /** r28n-5 step 3 [3]: the host and port written out, the from: with the placeholders of the project. */
    @Test
    void theHostOfThePropertiesIsTheSameServer(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("application.properties"), PROPERTIES);
        String yaml = FROM + """
                        - to:
                            uri: sftp://localhost:2222/receipts
                            parameters:
                              fileName: "${file:name.noext}.txt"
                """;
        assertThat(SourceValidator.validate("sftp-transfer.camel.yaml", yaml, catalog, null, dir)).singleElement()
                .asString()
                .startsWith("Line 20: to: sftp://localhost:2222/receipts has no username, so it logs in as anonymous,"
                            + " while the from: on line 4 logs in to the same server");
        // without the project its placeholders are not known: not the same server as far as the check can tell
        assertThat(RemoteFileLogins.validate(yaml, catalog, null, null)).isEmpty();
        // another port is another server
        assertThat(RemoteFileLogins.validate(yaml.replace("localhost:2222", "localhost:2223"), catalog, dir,
                "sftp-transfer.camel.yaml")).isEmpty();
    }

    /** r28n-5 step 2 [23] (shortened): an onException moves a failed batch with a to: of the uri alone. */
    @Test
    void aToOfTheUriAloneGetsTheWholeEndpoint(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("application.properties"), PROPERTIES);
        String yaml = """
                - onException:
                    exception:
                      - java.lang.Exception
                    steps:
                      - to:
                          uri: sftp://localhost:2222/invoices
                """ + FROM;
        assertThat(RemoteFileLogins.validate(yaml, catalog, dir, "sftp-transfer.camel.yaml")).singleElement().asString()
                .startsWith("Line 6: to: sftp://localhost:2222/invoices has no username")
                .endsWith(": write it with the same login: to: {uri: \"sftp://localhost:2222/invoices\", parameters:"
                          + " {username: \"{{sftp.username}}\", password: \"{{sftp.password}}\","
                          + " useUserKnownHostsFile: false}}");
    }

    /** r26n-6 step 3 [7]: the options in the uri. */
    @Test
    void optionsInTheUriGetTheLoginInTheUri() {
        String yaml = FROM + """
                        - to:
                            uri: "sftp://{{sftp.host}}:{{sftp.port}}/receipts?fileName=${file:name.noext}.txt"
                """;
        assertThat(RemoteFileLogins.validate(yaml, catalog, null, null)).singleElement().asString()
                .endsWith(": add the same login to its uri: &username={{sftp.username}}&password={{sftp.password}}"
                          + "&useUserKnownHostsFile=false");
    }

    @Test
    void theFromOfAnotherRouteFileOfTheProject(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("application.properties"), PROPERTIES);
        Files.writeString(dir.resolve("download.camel.yaml"), FROM);
        String yaml = """
                - route:
                    from:
                      uri: direct:receipt
                      steps:
                        - to:
                            uri: "sftp:{{sftp.host}}:{{sftp.port}}/receipts"
                """;
        assertThat(RemoteFileLogins.validate(yaml, catalog, dir, "upload.camel.yaml")).singleElement().asString()
                .contains("while the from: of download.camel.yaml (line 4) logs in to the same server");
    }

    @Test
    void notReportedWhereTheLoginIsThereOrNotKnown(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("application.properties"), PROPERTIES);
        String to = """
                        - to:
                            uri: "%s"
                """;
        for (String uri : List.of(
                // a login of its own, in the uri or under parameters: below
                "sftp://admin@{{sftp.host}}:{{sftp.port}}/receipts",
                "sftp://{{sftp.host}}:{{sftp.port}}/receipts?username={{sftp.username}}&password={{sftp.password}}",
                // another server, another scheme
                "sftp://files.example.com/receipts", "ftp://{{sftp.host}}:{{sftp.port}}/receipts",
                // the whole uri a placeholder
                "{{receipts.uri}}")) {
            assertThat(RemoteFileLogins.validate(FROM + to.formatted(uri), catalog, dir, "sftp-transfer.camel.yaml"))
                    .as(uri).isEmpty();
        }
        // the reference of the step: the same login under parameters
        String reference = FROM + """
                        - to:
                            uri: sftp
                            parameters:
                              host: "{{sftp.host}}"
                              port: "{{sftp.port}}"
                              directoryName: receipts
                              username: "{{sftp.username}}"
                              password: "{{sftp.password}}"
                              useUserKnownHostsFile: false
                              fileName: "${file:name.noext}.txt"
                """;
        assertThat(RemoteFileLogins.validate(reference, catalog, dir, "sftp-transfer.camel.yaml")).isEmpty();
        // no endpoint of the server logs in: nothing to copy, and an anonymous server is possible
        String anonymous = FROM.replace("        username: \"{{sftp.username}}\"\n", "")
                .replace("        password: \"{{sftp.password}}\"\n", "") + to.formatted("sftp://localhost:2222/receipts");
        assertThat(RemoteFileLogins.validate(anonymous, catalog, dir, "sftp-transfer.camel.yaml")).isEmpty();
    }
}
