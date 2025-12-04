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

import javax.inject.Inject;

import org.apache.camel.spi.BacklogDebugger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.aether.RepositorySystem;

/**
 * The maven goal allowing to automatically set up the Camel application to debug the Camel routes thanks to the Camel
 * textual Route Debugger.
 */
@Mojo(
        name = "debug",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class DebugMojo extends DevMojo {

    /**
     * Indicates whether the message processing done by Camel should be suspended as long as a debugger is not attached.
     */
    @Parameter(property = "camel.suspend", defaultValue = "true")
    private boolean suspend;

    @Inject
    public DebugMojo(RepositorySystem repositorySystem) {
        super(repositorySystem);
    }

    @Override
    protected void beforeBootstrapCamel() throws Exception {
        super.beforeBootstrapCamel();

        // Enable JMX
        System.setProperty("org.apache.camel.jmx.disabled", "false");
        // Enable the suspend mode.
        System.setProperty(BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME, Boolean.toString(suspend));
        String suspendMode = System.getenv(BacklogDebugger.SUSPEND_MODE_ENV_VAR_NAME);
        if (suspendMode != null && Boolean.parseBoolean(suspendMode) != suspend) {
            throw new MojoExecutionException(String.format(
                    "The environment variable %s has been set and prevents to configure the suspend mode. Please remove it first.",
                    BacklogDebugger.SUSPEND_MODE_ENV_VAR_NAME));
        }
    }

    @Override
    protected String goal() {
        return "camel:debug";
    }
}
