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
package org.apache.camel.component.undertow;

import javax.net.ssl.SSLContext;

/**
 * The key to identify an Undertow host.
 */
public final class UndertowHostKey {
    private final String host;
    private final int port;

    // SSLContext should not be part of the equals/hashCode contract
    private final SSLContext sslContext;

    public UndertowHostKey(String host, int port, SSLContext ssl) {
        this.host = host;
        this.port = port;
        this.sslContext = ssl;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    @Override
    public boolean equals(Object target) {
        if (!(target instanceof UndertowHostKey)) {
            return false;
        }
        UndertowHostKey targetKey = (UndertowHostKey) target;
        boolean answer = true;
        return answer && this.host != null && targetKey.host != null
            && this.host.equals(targetKey.host) && this.port == targetKey.port;
    }

    @Override
    public int hashCode() {
        int answer = host.hashCode();
        answer = answer * 31 + Integer.hashCode(port);
        return answer;
    }
}