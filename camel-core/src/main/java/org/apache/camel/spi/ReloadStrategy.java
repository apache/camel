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
package org.apache.camel.spi;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;
import org.apache.camel.StaticService;

/**
 * SPI strategy for reloading Camel routes in an existing running {@link org.apache.camel.CamelContext}
 */
public interface ReloadStrategy extends Service, StaticService, CamelContextAware {

    /**
     * A reload is triggered when a XML resource is changed which contains Camel routes.
     *
     * @param camelContext  the running CamelContext
     * @param name          name of resource such as a file name (can be null)
     * @param resource      the changed resource
     */
    void onReloadXml(CamelContext camelContext, String name, InputStream resource);

    /**
     * Number of reloads succeeded.
     */
    int getReloadCounter();

    /**
     * Number of reloads failed.
     */
    int getFailedCounter();

    /**
     * Reset the counters.
     */
    void resetCounters();
}
