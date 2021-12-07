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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;

/**
 * To setup vertx http server in the running Camel application
 */
public final class VertxHttpServer {

    private VertxHttpServer() {
    }

    public static void registerServer(CamelContext camelContext) {
        try {
            // must load via the classloader set on camel context that will have the classes on its classpath
            Class clazz = camelContext.getClassResolver()
                    .resolveMandatoryClass(
                            "org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration");
            Object config = clazz.getConstructors()[0].newInstance();

            clazz = camelContext.getClassResolver()
                    .resolveMandatoryClass("org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer");
            Object server = clazz.getConstructors()[0].newInstance(config);

            camelContext.addService(server);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }
}
