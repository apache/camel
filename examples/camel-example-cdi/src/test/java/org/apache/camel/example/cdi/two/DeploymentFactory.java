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
package org.apache.camel.example.cdi.two;

import org.apache.camel.example.cdi.ArchiveUtil;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;

/**
 *  Factory to select appropriate archive according to container
 */
public class DeploymentFactory {

    private static final String WELD_EMBEDDED_CONTAINER = "weld-ee-embedded";
    private static final String JBOSSAS_MANAGED_CONTAINER = "jbossas-managed";

    protected DeploymentFactory() {

    }

    @Deployment
    public static Archive<?> createArchive() {

        String deploymentType = System.getProperty("arquillian");
        Archive<?> archive = null;

        // TODO FIND A BETTER WAY TO PASS PACKAGES
        String[] packages = {"org.apache.camel.example.cdi", "org.apache.camel.example.cdi.two"};

        if (deploymentType.equals(WELD_EMBEDDED_CONTAINER)) {
            archive = ArchiveUtil.createJarArchive(packages);

        } else if (deploymentType.equals(JBOSSAS_MANAGED_CONTAINER)) {
            archive =  ArchiveUtil.createWarArchive(packages);
        }
        return archive;
    }

}

