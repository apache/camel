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
package org.apache.camel.main.download;

import java.util.Optional;

import org.apache.camel.impl.engine.DefaultPeriodTaskResolver;
import org.apache.camel.spi.FactoryFinder;

public class ExportPeriodTaskResolver extends DefaultPeriodTaskResolver {

    public ExportPeriodTaskResolver(FactoryFinder finder) {
        super(finder);
    }

    @Override
    public Optional<Object> newInstance(String key) {
        if (skip(key)) {
            return Optional.empty();
        }
        return super.newInstance(key);
    }

    @Override
    public <T> Optional<T> newInstance(String key, Class<T> type) {
        if (skip(key)) {
            return Optional.empty();
        }
        return super.newInstance(key, type);
    }

    private boolean skip(String key) {
        // skip all vault refresh during export as they will attempt to connect to remote system
        return "aws-secret-refresh".equals(key) || "gcp-secret-refresh".equals(key) || "azure-secret-refresh".equals(key)
                || "kubernetes-secret-refresh".equals(key) || "kubernetes-configmaps-refresh".equals(key);
    }
}
