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
package org.apache.camel.main.fatjar;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanResourceResolver;

/**
 * A Main class for booting up Camel in standalone mode packaged as far-jar using the camel-repackager-maven-plugin.
 */
public class Main extends org.apache.camel.main.Main {

    /**
     * Camel main application
     *
     * It is recommended to use {@link org.apache.camel.main.Main#Main(Class)} to specify the main class.
     */
    public Main() {
    }

    /**
     * Camel main application
     *
     * @param mainClass the main class
     */
    public Main(Class<?> mainClass) {
        super(mainClass);
    }

    /**
     * Camel main application
     *
     * @param mainClass            the main class
     * @param configurationClasses additional camel configuration classes
     */
    @SafeVarargs
    public Main(Class<?> mainClass, Class<? extends CamelConfiguration>... configurationClasses) {
        super(mainClass, configurationClasses);
    }

    @Override
    protected CamelContext createCamelContext() {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addContextPlugin(PackageScanClassResolver.class,
                new FatJarPackageScanClassResolver());
        context.getCamelContextExtension().addContextPlugin(PackageScanResourceResolver.class,
                new FatJarPackageScanResourceResolver());
        return context;
    }
}
