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

package org.apache.camel.spi;

import org.apache.camel.CamelContext;

/**
 * To let camel-groovy pre-compile script files during bootstrap.
 */
public interface GroovyScriptCompiler {

    /**
     * Service factory key.
     */
    String FACTORY = "groovy-script-compiler";

    /**
     * Directories to scan for groovy source to be pre-compiled. The directories are using Ant-path style pattern, and
     * multiple directories can be specified separated by comma.
     */
    void setScriptPattern(String scriptPattern);

    /**
     * Directories to scan for groovy source to be pre-compiled. The directories are using Ant-path style pattern, and
     * multiple directories can be specified separated by comma.
     */
    String getScriptPattern();

    /**
     * Whether to preload existing compiled Groovy sources from classpath and the {@link CompileStrategy#getWorkDir()},
     * on startup. This can be enabled to avoid compiling sources that already has been compiled during a build phase.
     */
    void setPreloadCompiled(boolean preloadCompiled);

    /**
     * Whether to preload existing compiled Groovy sources from the {@link CompileStrategy#getWorkDir()}, on startup.
     * This can be enabled to avoid compiling sources that already has been compiled during a build phase.
     */
    boolean isPreloadCompiled();

    /**
     * Compiles or re-compiles the given groovy source
     *
     * @param  resource  the groovy source
     * @throws Exception is thrown if compilation error
     */
    void recompile(Resource resource) throws Exception;

    /**
     * Checks whether any groovy sources exists?
     *
     * @param  context       the camel context
     * @param  scriptPattern directories to scan
     * @return               true if any resources exists, false otherwise
     */
    static boolean existsSourceFiles(CamelContext context, String scriptPattern) throws Exception {
        boolean exists = false;
        PackageScanResourceResolver resolver =
                context.getCamelContextExtension().getContextPlugin(PackageScanResourceResolver.class);
        for (String pattern : scriptPattern.split(",")) {
            // include all kind of resources
            for (Resource resource : resolver.findResources(pattern, n -> true)) {
                if (resource.exists()) {
                    exists = true;
                    break;
                }
            }
        }
        return exists;
    }
}
