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
package org.apache.camel.component.vertx.common;

import java.util.function.Function;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.net.TrustOptions;

public class TrustManagerFactoryOptions implements TrustOptions {
    private final TrustManagerFactory trustManagerFactory;

    public TrustManagerFactoryOptions(TrustManagerFactory trustManagerFactory) {
        this.trustManagerFactory = trustManagerFactory;
    }

    private TrustManagerFactoryOptions(TrustManagerFactoryOptions other) {
        trustManagerFactory = other.trustManagerFactory;
    }

    @Override
    public TrustOptions copy() {
        return new TrustManagerFactoryOptions(this);
    }

    @Override
    public TrustManagerFactory getTrustManagerFactory(Vertx vertx) {
        return trustManagerFactory;
    }

    @Override
    public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) {
        return serverName -> trustManagerFactory.getTrustManagers();
    }
}
