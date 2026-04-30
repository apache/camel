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
package org.apache.camel.test.infra.cli.services;

import java.util.stream.Stream;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class CliServiceFactory {

    private static class SingletonCliService extends SingletonService<CliService> implements CliService {
        public SingletonCliService(CliService service, String name) {
            super(service, name);
        }

        @Override
        public String execute(String command) {
            return getService().execute(command);
        }

        @Override
        public String execute(String command, Boolean getError, Boolean expectFail) {
            return getService().execute(command, getError, expectFail);
        }

        @Override
        public String executeBackground(String command) {
            return getService().executeBackground(command);
        }

        @Override
        public String executeGenericCommand(String command) {
            return getService().executeGenericCommand(command);
        }

        @Override
        public String executeGenericCommand(String command, Boolean getError, Boolean expectFail) {
            return getService().executeGenericCommand(command, getError, expectFail);
        }

        @Override
        public void copyFileInternally(String source, String destination) {
            getService().copyFileInternally(source, destination);
        }

        @Override
        public String getMountPoint() {
            return getService().getMountPoint();
        }

        @Override
        public String getContainerLogs() {
            return getService().getContainerLogs();
        }

        @Override
        public int getDevConsolePort() {
            return getService().getDevConsolePort();
        }

        @Override
        public Stream<String> listDirectory(String directoryPath) {
            return getService().listDirectory(directoryPath);
        }

        @Override
        public String id() {
            return getService().id();
        }

        @Override
        public String version() {
            return getService().version();
        }

        @Override
        public int getSshPort() {
            return getService().getSshPort();
        }

        @Override
        public String getSshPassword() {
            return getService().getSshPassword();
        }
    }

    private CliServiceFactory() {

    }

    public static SimpleTestServiceBuilder<CliService> builder() {
        return new SimpleTestServiceBuilder<>(CliLocalContainerService.CONTAINER_NAME);
    }

    public static CliService createService() {
        return builder()
                .addLocalMapping(CliLocalContainerService::new)
                .addMapping("local-camel-cli-process", CliLocalProcessService::new)
                .build();
    }

    public static CliService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final CliService INSTANCE;
        static {
            SimpleTestServiceBuilder<CliService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonCliService(new CliLocalContainerService(), CliLocalContainerService.CONTAINER_NAME))
                    .addMapping("local-camel-cli-process",
                            () -> new SingletonCliService(
                                    new CliLocalProcessService(),
                                    CliLocalContainerService.CONTAINER_NAME));
            INSTANCE = instance.build();
        }
    }
}
