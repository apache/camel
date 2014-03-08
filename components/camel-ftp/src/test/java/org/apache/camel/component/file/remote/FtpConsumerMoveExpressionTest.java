/**
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for FTP using expression (file language)
 */
public class FtpConsumerMoveExpressionTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/filelanguage?password=admin&consumer.delay=5000";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/filelanguage");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myguidgenerator", new MyGuidGenerator());
        return jndi;
    }
    
    @Test
    public void testMoveUsingExpression() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Reports");

        sendFile(getFtpUrl(), "Reports", "report2.txt");

        assertMockEndpointsSatisfied();

        // give time for consumer to rename file
        Thread.sleep(1000);

        String now = new SimpleDateFormat("yyyyMMdd").format(new Date());
        File file = new File(FTP_ROOT_DIR + "/filelanguage/backup/" + now + "/123-report2.bak");
        assertTrue("File should have been renamed", file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl() + "&move=backup/${date:now:yyyyMMdd}/${bean:myguidgenerator}"
                        + "-${file:name.noext}.bak").to("mock:result");
            }
        };
    }

    public class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }
}