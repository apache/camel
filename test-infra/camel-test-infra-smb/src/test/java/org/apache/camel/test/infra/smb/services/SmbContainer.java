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

package org.apache.camel.test.infra.smb.services;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class SmbContainer extends GenericContainer<SmbContainer> {

    public static final int SMB_PORT_DEFAULT = 445;
    public static final String DEFAULT_USER = "camel";
    public static final String DEFAULT_PASSWORD = "camelTester123";

    public SmbContainer() {
        super(new ImageFromDockerfile("localhost/samba:camel", false)
                .withFileFromClasspath(".",
                        "org/apache/camel/test/infra/smb/services/"));

        super.withExposedPorts(SMB_PORT_DEFAULT)
                .waitingFor(Wait.forListeningPort());
    }

    public String getUser() {
        return DEFAULT_USER;
    }

    public String getPassword() {
        return DEFAULT_PASSWORD;
    }

    public String getShare() {
        return "data-rw";
    }

    public int getPort() {
        return getMappedPort(SMB_PORT_DEFAULT);
    }
}
