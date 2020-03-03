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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test re-creating operations
 * 
 * @see {org.apache.camel.component.file.remote.RemoteFileConsumer#recoverableConnectIfNecessary}
 */
public class FromFtpClientSoTimeout3Test extends CamelTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/timeout/?soTimeout=5000";
    }

    private String getPort() {
        return "21";
    }

    @Test
    public void test() throws Exception {
        @SuppressWarnings("unchecked")
        FtpEndpoint<FTPFile> ftpEndpoint = context.getEndpoint(getFtpUrl(), FtpEndpoint.class);

        // set "ftp://admin@localhost:21/timeout/?ftpClient.soTimeout=10"
        Map<String, Object> ftpClientParameters = new HashMap<>();
        ftpClientParameters.put("soTimeout", "10");
        ftpEndpoint.setFtpClientParameters(ftpClientParameters);

        // test RemoteFileConsumer#buildConsumer
        assertEquals(ftpClientParameters.get("soTimeout"), "10");
        ftpEndpoint.createRemoteFileOperations();

        // test RemoteFileConsumer#recoverableConnectIfNecessary
        // recover by re-creating operations which should most likely be able to
        // recover
        assertEquals(ftpClientParameters.get("soTimeout"), "10");
        ftpEndpoint.createRemoteFileOperations();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
            }
        };
    }
}
