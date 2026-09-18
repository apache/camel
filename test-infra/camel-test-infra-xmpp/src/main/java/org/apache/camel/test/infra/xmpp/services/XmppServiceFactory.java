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
package org.apache.camel.test.infra.xmpp.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class XmppServiceFactory {

    private static class SingletonXmppService extends SingletonService<XmppService> implements XmppService {
        public SingletonXmppService(XmppService service, String name) {
            super(service, name);
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int port() {
            return getService().port();
        }

        @Override
        public String getUrl() {
            return getService().getUrl();
        }
    }

    private XmppServiceFactory() {

    }

    public static SimpleTestServiceBuilder<XmppService> builder() {
        return new SimpleTestServiceBuilder<>("xmpp");
    }

    public static XmppService createService() {
        return builder()
                .addLocalMapping(XmppLocalContainerService::new)
                .addRemoteMapping(XmppRemoteService::new)
                .build();
    }

    public static XmppService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final XmppService INSTANCE;
        static {
            SimpleTestServiceBuilder<XmppService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonXmppService(new XmppLocalContainerService(), "xmpp"))
                    .addRemoteMapping(XmppRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class XmppLocalContainerService extends XmppLocalContainerInfraService implements XmppService {
    }

    public static class XmppRemoteService extends XmppRemoteInfraService implements XmppService {
    }
}
