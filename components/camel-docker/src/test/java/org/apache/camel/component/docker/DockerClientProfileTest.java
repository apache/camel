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
package org.apache.camel.component.docker;

import org.apache.camel.component.docker.exception.DockerException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

/**
 * Validates the {@link DockerClientProfile}
 */
public class DockerClientProfileTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void clientProfileTest() {
        String host = "host";
        String email = "docker@camel.apache.org";
        String username = "user";
        String password = "password";
        Integer port = 2241;
        Integer requestTimeout = 40;
        boolean secure = true;
        String certPath = "/docker/cert/path";
        String cmdExecFactory = DockerConstants.DEFAULT_CMD_EXEC_FACTORY;

        DockerClientProfile clientProfile1 = new DockerClientProfile();
        clientProfile1.setHost(host);
        clientProfile1.setEmail(email);
        clientProfile1.setUsername(username);
        clientProfile1.setPassword(password);
        clientProfile1.setPort(port);
        clientProfile1.setRequestTimeout(requestTimeout);
        clientProfile1.setSecure(secure);
        clientProfile1.setCertPath(certPath);
        clientProfile1.setCmdExecFactory(cmdExecFactory);

        DockerClientProfile clientProfile2 = new DockerClientProfile();
        clientProfile2.setHost(host);
        clientProfile2.setEmail(email);
        clientProfile2.setUsername(username);
        clientProfile2.setPassword(password);
        clientProfile2.setPort(port);
        clientProfile2.setRequestTimeout(requestTimeout);
        clientProfile2.setSecure(secure);
        clientProfile2.setCertPath(certPath);
        clientProfile2.setCmdExecFactory(cmdExecFactory);

        assertEquals(clientProfile1, clientProfile2);
    }

    @Test
    public void clientProfileUrlTest() throws DockerException {
        DockerClientProfile profile = new DockerClientProfile();
        profile.setHost("localhost");
        profile.setPort(2375);
        assertEquals("tcp://localhost:2375", profile.toUrl());
    }

    @Test
    public void clientProfileNoPortSpecifiedUrlTest() throws DockerException {
        DockerClientProfile profile = new DockerClientProfile();
        profile.setHost("localhost");
        expectedException.expectMessage("port must be specified");
        profile.toUrl();
    }

    @Test
    public void clientProfileWithSocketUrlTest() throws DockerException {
        DockerClientProfile profile = new DockerClientProfile();
        profile.setHost("/var/run/docker.sock");
        // Port should be ignored
        profile.setPort(2375);
        profile.setSocket(true);
        assertEquals("unix:///var/run/docker.sock", profile.toUrl());
    }
}
