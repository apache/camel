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

import org.apache.camel.Component;
import org.apache.camel.spi.ContentCacheAware;
import org.apache.camel.support.LifecycleStrategySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle strategy that disables content caching on resource-based components when routes-reload is enabled. Lets
 * users edit a resource file (e.g. an XSLT stylesheet) and see the change applied live, without having to set
 * {@code contentCache=false} on every endpoint.
 *
 * Only flips components that have not been explicitly configured by the user (i.e.
 * {@link ContentCacheAware#getContentCache()} returns {@code null}); explicit user settings are preserved.
 */
class DevModeContentCacheStrategy extends LifecycleStrategySupport {

    private static final Logger LOG = LoggerFactory.getLogger(DevModeContentCacheStrategy.class);

    @Override
    public void onComponentAdd(String name, Component component) {
        if (component instanceof ContentCacheAware aware && aware.getContentCache() == null) {
            aware.setContentCache(Boolean.FALSE);
            LOG.debug("Routes-reload is enabled: disabling contentCache on component '{}' for live resource reload",
                    name);
        }
    }
}
