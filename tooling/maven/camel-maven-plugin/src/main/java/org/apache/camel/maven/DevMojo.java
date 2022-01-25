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
package org.apache.camel.maven;

import java.io.File;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "dev", defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class DevMojo extends RunMojo {

    /**
     * To watch the directory for file changes which triggers a live reload of the Camel routes on-the-fly.
     */
    @Parameter(property = "camel.routesDirectory")
    private String routesDirectory;

    /**
     * Enable routes reloading
     *
     * @throws Exception
     */
    @Override
    protected void beforeBootstrapCamel() throws Exception {
        String dir;
        if (routesDirectory != null) {
            dir = routesDirectory;
        } else if (project.getResources().size() == 1) {
            dir = project.getResources().get(0).getDirectory();
        } else {
            dir = "src/main/resources";
        }

        // use absolute path for dir
        dir = new File(dir).getAbsolutePath();

        System.setProperty("camel.main.routesReloadEnabled", "true");
        System.setProperty("camel.main.routesReloadDirectory", dir);
        System.setProperty("camel.main.routesReloadDirectoryRecursive", "true");
        System.setProperty("camel.main.sourceLocationEnabled", "true");
        System.setProperty("camel.main.durationMaxAction", "stop");
        System.setProperty("camel.main.routesReloadPattern",
                "*.xml,*.yaml,*.java");
    }

    @Override
    protected String goal() {
        return "camel:dev";
    }

}
