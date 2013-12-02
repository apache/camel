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
package org.apache.camel.maven;

import java.lang.reflect.Method;

/**
 * Runs a CamelContext using any Spring or Blueprint XML configuration files found in
 * <code>META-INF/spring/*.xml</code>, and <code>OSGI-INF/blueprint/*.xml</code>,
 * and <code>camel-*.xml</code> and starting up the context together with
 * <a href="http://hawt.io/">hawtio</a> as web console.
 *
 * @goal hawtio
 * @requiresDependencyResolution compile+runtime
 * @execute phase="test-compile"
 */
public class HawtioMojo extends RunMojo {

    /**
     * The port number to use for the hawtio web console.
     *
     * @parameter property="camel.port"
     *            default-value="8080"
     */
    private int port = 8080;

    public HawtioMojo() {
        extendedPluginDependencyArtifactId = "hawtio-app";
    }

    @Override
    protected void beforeBootstrapCamel() throws Exception {
        getLog().info("Starting hawtio ...");
        Method hawtioMain = Thread.currentThread().getContextClassLoader().loadClass("io.hawt.app.App")
                .getMethod("main", new Class[] {String[].class});
        if (!hawtioMain.isAccessible()) {
            getLog().debug("Setting accessibility to true in order to invoke main().");
            hawtioMain.setAccessible(true);
        }
        String[] args = new String[]{"--port", "" + port, "--join", "false"};
        hawtioMain.invoke(hawtioMain, new Object[]{args});
    }

}
