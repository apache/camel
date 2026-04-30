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
package org.apache.camel.test.infra.chatscript.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class ChatScriptServiceFactory {

    private static class SingletonChatScriptService extends SingletonService<ChatScriptService>
            implements ChatScriptService {
        public SingletonChatScriptService(ChatScriptService service, String name) {
            super(service, name);
        }

        @Override
        public String serviceAddress() {
            return getService().serviceAddress();
        }
    }

    private ChatScriptServiceFactory() {

    }

    public static SimpleTestServiceBuilder<ChatScriptService> builder() {
        return new SimpleTestServiceBuilder<>("chatscript");
    }

    public static ChatScriptService createService() {
        return builder()
                .addLocalMapping(ChatScriptLocalContainerTestService::new)
                .addRemoteMapping(ChatScriptRemoteTestService::new)
                .build();
    }

    public static ChatScriptService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final ChatScriptService INSTANCE;
        static {
            SimpleTestServiceBuilder<ChatScriptService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonChatScriptService(new ChatScriptLocalContainerTestService(), "chatscript"))
                    .addRemoteMapping(ChatScriptRemoteTestService::new);
            INSTANCE = instance.build();
        }
    }

    public static class ChatScriptLocalContainerTestService extends ChatScriptLocalContainerInfraService
            implements ChatScriptService {
    }

    public static class ChatScriptRemoteTestService extends ChatScriptRemoteInfraService implements ChatScriptService {
    }
}
