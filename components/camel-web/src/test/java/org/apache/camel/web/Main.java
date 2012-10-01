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
package org.apache.camel.web;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.webapp.WebAppContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple bootstrap class for starting Jetty in your IDE using the local web
 * application.
 *
 * @version 
 */
public final class Main {

    public static int mainPort = 9998;
    public static final String WEBAPP_DIR = "src/main/webapp";
    public static final String WEBAPP_CTX = "/";
    protected static final Server SERVER = new Server();
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        // now lets start the web server
        if (args.length > 0) {
            String text = args[0];
            int port = Integer.parseInt(text);
            if (port > 0) {
                mainPort = port;
            }
        }
        start();
    }

    public static void start() throws Exception {
        LOG.info("Starting Web Server on port: " + mainPort);

        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(mainPort);
        connector.setServer(SERVER);
        WebAppContext context = new WebAppContext();

        context.setResourceBase(WEBAPP_DIR);
        context.setContextPath(WEBAPP_CTX);
        context.setServer(SERVER);

        SERVER.setHandler(context);
        SERVER.setConnectors(new Connector[]{connector});
        SERVER.start();

        LOG.info("");
        LOG.info("==============================================================================");
        LOG.info("Started the Camel REST Console: point your web browser at " + getRootUrl());
        LOG.info("==============================================================================");
        LOG.info("");
    }

    public static void stop() throws Exception {
        SERVER.stop();
    }

    /**
     * Returns the root URL of the application
     */
    public static String getRootUrl() {
        return "http://localhost:" + mainPort + WEBAPP_CTX;
    }
}
