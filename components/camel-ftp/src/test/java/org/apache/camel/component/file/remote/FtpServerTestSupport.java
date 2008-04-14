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
import org.apache.ftpserver.ConfigurableFtpServerContext;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.config.PropertiesConfiguration;
import org.apache.ftpserver.ftplet.Configuration;
import org.apache.ftpserver.interfaces.FtpServerContext;

/**
 * Base class for unit testing using a FTPServer
 */
public abstract class FtpServerTestSupport extends ContextTestSupport {
    protected FtpServer ftpServer;

    public abstract String getPort();

    protected void setUp() throws Exception {
        super.setUp();
        initFtpServer();
        ftpServer.start();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        // must stop server after super to let the clients stop correctly (CAMEL-444)
        ftpServer.stop();
    }

    protected void initFtpServer() throws Exception {
        // get the configuration object
        Properties properties = createFtpServerProperties();
        Configuration config = new PropertiesConfiguration(properties);

        // create service context
        FtpServerContext ftpConfig = new ConfigurableFtpServerContext(config);

        // create the server object and start it
        ftpServer = new FtpServer(ftpConfig);
    }

    protected Properties createFtpServerProperties() {
        Properties properties = new Properties();
        properties.setProperty("config.listeners.default.port", getPort());
        properties.setProperty("config.create-default-user", "true");
        return properties;
    }
}
