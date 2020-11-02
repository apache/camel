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
package org.apache.camel.impl.engine;

import java.io.IOException;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.FactoryFinder;

/**
 * Bootstrap configurer resolver that looks for configurer factories in
 * <b>META-INF/services/org/apache/camel/configurer/</b>.
 */
public class BootstrapConfigurerResolver extends DefaultConfigurerResolver implements BootstrapCloseable {

    public BootstrapConfigurerResolver(FactoryFinder factoryFinder) {
        super(factoryFinder);
    }

    @Override
    public void close() throws IOException {
        if (factoryFinder instanceof BootstrapCloseable) {
            ((BootstrapCloseable) factoryFinder).close();
        }
        factoryFinder = null;
    }
}
