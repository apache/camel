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
package org.apache.camel.component.file.remote;

import java.io.FileOutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;

/**
 *
 */
public class FtpChangedZeroLengthReadLockTest extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/changed?password=admin&readLock=changed&readLockCheckInterval=1000&readLockMinLength=0&delete=true";
    }

    @Test
    public void testChangedReadLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/changed/out/zerofile.dat");

        writeZeroFile();

        assertMockEndpointsSatisfied();
    }

    private void writeZeroFile() throws Exception {
        createDirectory(FTP_ROOT_DIR + "/changed");
        FileOutputStream fos = new FileOutputStream(FTP_ROOT_DIR + "/changed/zerofile.dat", true);
        fos.flush();
        fos.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getFtpUrl()).to("file:target/changed/out", "mock:result");
            }
        };
    }

}
