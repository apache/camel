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

package org.apache.camel.test.infra.smb.services;

import java.io.InputStream;

import com.github.dockerjava.api.exception.NotFoundException;
import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.testcontainers.utility.ThrowingFunction;

public class SmbServiceFactory {

    static class SingletonSmbService extends SingletonService<SmbService> implements SmbService {
        public SingletonSmbService(SmbService service, String name) {
            super(service, name);
        }

        @Override
        public String address() {
            return getService().address();
        }

        @Override
        public String password() {
            return getService().password();
        }

        @Override
        public String shareName() {
            return getService().shareName();
        }

        @Override
        public String smbFile(String file) {
            return getService().smbFile(file);
        }

        @Override
        public String userName() {
            return getService().userName();
        }

        @Override
        public <T> T copyFileFromContainer(String fileName, ThrowingFunction<InputStream, T> function) {
            return getService().copyFileFromContainer(fileName, function);
        }
    }

    public static SmbService createService() {
        return builder()
                .addLocalMapping(SmbLocalContainerService::new)
                .addRemoteMapping(SmbRemoteService::new)
                .build();
    }

    public static class SmbRemoteService extends SmbRemoteInfraService implements SmbService {
        @Override
        public <T> T copyFileFromContainer(String fileName, ThrowingFunction<InputStream, T> function) {
            return null;
        }
    }

    public static SimpleTestServiceBuilder<SmbService> builder() {
        return new SimpleTestServiceBuilder<>("smb");
    }

    public static SmbService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final SmbService INSTANCE;
        static {
            SimpleTestServiceBuilder<SmbService> instance = builder();
            instance.addLocalMapping(() -> new SingletonSmbService(new SmbLocalContainerService(), "smb"))
                    .addRemoteMapping(SmbRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class SmbLocalContainerService extends SmbLocalContainerInfraService implements SmbService {

        @Override
        public <T> T copyFileFromContainer(String fileName, ThrowingFunction<InputStream, T> function) {
            try {
                return container.copyFileFromContainer(fileName, function);
            } catch (NotFoundException e) {
                LOG.info("No file found with name {}:", fileName);
                return null;
            }
        }
    }
}
