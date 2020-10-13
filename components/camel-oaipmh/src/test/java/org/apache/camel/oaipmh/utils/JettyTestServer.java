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
package org.apache.camel.oaipmh.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.TestSupport;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.fail;

public final class JettyTestServer {

    private static final Logger LOG = LoggerFactory.getLogger(JettyTestServer.class);
    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static final int PORT_SSL = AvailablePortFinder.getNextAvailable();
    private static final String PASSWORD = "changeit";
    private static JettyTestServer instance;

    public int port;
    public int portssl;
    public String context;
    public boolean https;

    private Server server;

    private JettyTestServer() {
    }

    private void unzip() throws FileNotFoundException, IOException {
        String fileZip = "src/test/resources/data.zip";
        File destDir = new File("src/test/resources");
        try (ZipFile zipFile = new ZipFile(fileZip)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    entryDestination.mkdirs();
                } else {
                    entryDestination.getParentFile().mkdirs();
                    try (InputStream in = zipFile.getInputStream(entry);
                         OutputStream out = new FileOutputStream(entryDestination)) {
                        IOUtils.copy(in, out);
                    }
                }
            }
        }
    }

    public void startServer() throws IOException {
        unzip();
        server = new Server(PORT);
        port = PORT;

        if (https) {
            portssl = PORT_SSL;
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(PORT);
            HttpConfiguration https = new HttpConfiguration();
            https.addCustomizer(new SecureRequestCustomizer());
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(JettyTestServer.class.getResource(
                    "/jettyKS/localhost.p12").toExternalForm());
            sslContextFactory.setKeyStorePassword(PASSWORD);
            sslContextFactory.setKeyManagerPassword(PASSWORD);
            ServerConnector sslConnector = new ServerConnector(
                    server,
                    new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(https));

            sslConnector.setPort(PORT_SSL);
            server.setConnectors(new Connector[] { connector, sslConnector });
        }

        ServletContextHandler servletContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContext.setContextPath("/");
        server.setHandler(servletContext);
        servletContext.addServlet(new ServletHolder(new MyHttpServlet(this.context)), "/*");
        try {
            server.start();
        } catch (Exception ex) {
            LOG.error("Could not start Server!", ex);
            fail(ex.getLocalizedMessage());
        }
    }

    public void stopServer() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ex) {
                LOG.warn("Server doesn't stop normal...", ex);
            } finally {
                server = null;
                port = 0;
            }
        }
        TestSupport.deleteDirectory("src/test/resources/data");
    }

    public static JettyTestServer getInstance() {
        if (instance == null) {
            instance = new JettyTestServer();
        }
        return instance;
    }

    private class MyHttpServlet extends HttpServlet {

        private static final long serialVersionUID = 5594945031962091041L;

        private String context;

        public MyHttpServlet(String context) {
            this.context = context;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String qs = req.getRequestURI() + "?" + req.getQueryString();
            String sha256Hex = DigestUtils.sha256Hex(qs);
            resp.getWriter().write(FileUtils.readFileToString(
                    new File("src/test/resources/data/" + this.context + "/" + sha256Hex + ".xml"), StandardCharsets.UTF_8));
        }
    }

}
