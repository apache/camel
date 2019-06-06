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
package org.apache.camel.component.rss;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.fail;

public final class JettyTestServer {

    private static final Logger LOG = LoggerFactory.getLogger(JettyTestServer.class);
    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static JettyTestServer instance;

    public int port;

    private Server server;

    private JettyTestServer() {
    }

    public void startServer() {
        server = new Server(PORT);
        port = PORT;

        ServletContextHandler servletContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContext.setSecurityHandler(basicAuth("camel", "camelPass", "Private!"));
        servletContext.setContextPath("/");
        server.setHandler(servletContext);
        servletContext.addServlet(new ServletHolder(new MyHttpServlet()), "/*");
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
    }

    private SecurityHandler basicAuth(String username, String password, String realm) {
        HashLoginService l = new HashLoginService();
        UserStore us = new UserStore();
        us.addUser(username, Credential.getCredential(password), new String[]{"user"});
        l.setUserStore(us);
        l.setName(realm);

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("myrealm");
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;
    }

    public static JettyTestServer getInstance() {
        if (instance == null) {
            instance = new JettyTestServer();
        }
        return instance;
    }

    private class MyHttpServlet extends HttpServlet {

        private static final long serialVersionUID = 5594945031962091041L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.getWriter().write(FileUtils.readFileToString(new File("src/test/data/rss20.xml"), StandardCharsets.UTF_8));
        }
    }

}
