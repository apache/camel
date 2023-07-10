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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.File;
import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpConsumerWithCharsetIT extends SftpServerTestSupport {

    private static final String SAMPLE_FILE_NAME
            = String.format("sample-%s.txt", SftpConsumerWithCharsetIT.class.getSimpleName());
    private static final String SAMPLE_FILE_CHARSET = "iso-8859-1";
    private static final String SAMPLE_FILE_PAYLOAD = "\u00e6\u00f8\u00e5 \u00a9"; // danish
                                                                                  // ae
                                                                                  // oe
                                                                                  // aa
                                                                                  // and
                                                                                  // (c)
                                                                                  // sign

    @Test
    public void testConsumeWithCharset() throws Exception {
        // prepare sample file to be consumed by SFTP consumer
        createSampleFile();

        // Prepare expectations
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(SAMPLE_FILE_PAYLOAD);

        context.getRouteController().startRoute("foo");

        // Check that expectations are satisfied
        MockEndpoint.assertIsSatisfied(context);

        // Check that the proper charset was set in the internal object
        Exchange exchange = mock.getExchanges().get(0);
        RemoteFile<?> file = (RemoteFile<?>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        assertNotNull(file);
        assertEquals(SAMPLE_FILE_CHARSET, file.getCharset());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&password=admin&charset="
                     + SAMPLE_FILE_CHARSET + "&knownHostsFile="
                     + service.getKnownHostsFile()).routeId("foo").noAutoStartup()
                        .to("mock:result");
            }
        };
    }

    private void createSampleFile() throws IOException {
        File file = new File(service.getFtpRootDir() + "/" + SAMPLE_FILE_NAME);

        FileUtils.write(file, SAMPLE_FILE_PAYLOAD, SAMPLE_FILE_CHARSET);
    }
}
