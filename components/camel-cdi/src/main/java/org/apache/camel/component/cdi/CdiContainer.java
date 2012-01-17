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

package org.apache.camel.component.cdi;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.spi.ContainerLifecycle;

import javax.enterprise.inject.spi.BeanManager;

/**
 * OpenWebBeans CDI container. It can be used in a Camel standalone project to start
 * and stop container. The container exposes a Bean?Manager that we can use to instantiate the
 * CdiRegistry used by Camel
 */
public class CdiContainer {

    private static ContainerLifecycle lifecycle = null;

    public static void start() throws Exception {
        try {
            lifecycle = WebBeansContext.currentInstance().getService(ContainerLifecycle.class);
            lifecycle.startApplication(null);

        } catch (Exception e) {
            throw e;
        }
    }

    public static void shutdown() throws Exception {
        try {
            lifecycle.stopApplication(null);

        } catch (Exception e) {
            throw e;
        }
    }

    public static BeanManager getBeanManager() {
        return lifecycle.getBeanManager();
    }

}
