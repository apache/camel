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
package org.apache.camel.itest.springboot.arquillian;

import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.MainMethodRunner;

/**
 * A Spring-boot jar launcher that uses the current thread instead of creating a new thread for spring-boot.
 */
public class ArquillianSyncBootJarLauncher extends JarLauncher {

    private ClassLoader classLoader;

    public ArquillianSyncBootJarLauncher() {
    }

    public void run(String[] args) throws Exception {
        this.launch(args);
    }

    @Override
    protected void launch(String[] args, String mainClass, ClassLoader classLoader) throws Exception {
        this.classLoader = classLoader;

        MainMethodRunner runner = createMainMethodRunner(mainClass, args, classLoader);

        Thread.currentThread().setContextClassLoader(classLoader);
        runner.run();
    }

    /**
     * Returns the classloader used by spring, to communicate with it.
     *
     * @return the spring classloader
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
