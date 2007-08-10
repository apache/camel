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

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.ftpserver.ConfigurableFtpServerContext;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.config.PropertiesConfiguration;
import org.apache.ftpserver.ftplet.Configuration;
import org.apache.ftpserver.interfaces.FtpServerContext;

/**
 * @version $Revision: 1.1 $
 */
public class FtpRouteTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String ftpUrl;
    protected FtpServer ftpServer;
    protected String expectedBody = "Hello there!";
    protected String port = "20010";

    public void testFtpRoute() throws Exception {

        resultEndpoint.expectedBodiesReceived(expectedBody);

        // TODO when we support multiple marshallers for messages
        // we can support passing headers over files using serialized/XML files
        //resultEndpoint.message(0).header("cheese").isEqualTo(123);

        sendExchange(expectedBody);

        resultEndpoint.assertIsSatisfied();
    }

    protected void sendExchange(final Object expectedBody) {
        template.sendBodyAndHeader(ftpUrl, expectedBody, "cheese", 123);
    }

    @Override
    protected void setUp() throws Exception {
        ftpUrl = createFtpUrl();
        ftpServer = createFtpServer();
        ftpServer.start();

        super.setUp();

        resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");
        createFtpServer();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (ftpServer != null) {
            ftpServer.stop();
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ftpUrl).to("mock:result");
            }
        };
    }

    protected String createFtpUrl() {
        return "ftp://admin@localhost:" + port + "/tmp/camel?password=admin";
    }

    protected FtpServer createFtpServer() throws Exception {
        // get the configuration object
        Properties properties = createFtpServerProperties();
        Configuration config = new PropertiesConfiguration(properties);

        // create servce context
        FtpServerContext ftpConfig = new ConfigurableFtpServerContext(config);

        // create the server object and start it
        return new FtpServer(ftpConfig);
    }

    protected Properties createFtpServerProperties() {
        Properties properties = new Properties();
        //properties.setProperty("config.data-connection.passive.ports", "20010");
        properties.setProperty("config.listeners.default.port", port);
        properties.setProperty("config.create-default-user", "true");
        return properties;
    }
}
