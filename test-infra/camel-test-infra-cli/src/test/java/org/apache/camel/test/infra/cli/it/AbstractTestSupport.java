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
package org.apache.camel.test.infra.cli.it;

import java.util.function.Consumer;

import org.apache.camel.test.infra.cli.services.CliService;
import org.apache.camel.test.infra.cli.services.CliServiceFactory;

public abstract class AbstractTestSupport {

    static boolean isLocalProcessWithSkipInstall() {
        return "true".equals(System.getProperty("cli.service.skip.install"))
                && "local-camel-cli-process".equals(System.getProperty("camel-cli.instance.type"));
    }

    protected void execute(Consumer<CliService> consumer) {
        try (CliService containerService = CliServiceFactory.createService()) {
            containerService.beforeAll(null);
            consumer.accept(containerService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
