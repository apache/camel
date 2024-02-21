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
import java.io.FileOutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpSimpleConsumeNoStartingDirIT extends SftpServerTestSupport {

    @Test
    public void testSftpSimpleConsume() throws Exception {
        // create files using regular file
        File file = new File("a.txt");
        FileOutputStream fos = new FileOutputStream(file, false);
        fos.write("ABC".getBytes());
        fos.close();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);

        FileUtil.deleteFile(file);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/"
                     + "?fileName=a.txt&username=admin&password=admin&delay=10000&disconnect=true&knownHostsFile="
                     + service.getKnownHostsFile()).routeId("foo")
                        .noAutoStartup().to("log:result", "mock:result");
            }
        };
    }
}
