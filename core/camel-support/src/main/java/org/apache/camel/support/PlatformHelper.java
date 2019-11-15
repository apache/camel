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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility dedicated for resolving runtime information related to the platform on which Camel is currently running.
 */
public final class PlatformHelper {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformHelper.class);

    private PlatformHelper() {
    }

    /**
     * Determine whether Camel is OSGi-aware. Current implementation of the method checks if the name of the
     * {@link CamelContext} matches the names of the known OSGi-aware contexts.
     *
     * @param camelContext context to be tested against OSGi-awareness
     * @return true if given context is OSGi-aware, false otherwise
     */
    public static boolean isOsgiContext(CamelContext camelContext) {
        String contextType = camelContext.getClass().getSimpleName();
        if (contextType.startsWith("Osgi") || contextType.equals("BlueprintCamelContext")) {
            LOG.trace("{} used - assuming running in the OSGi container.", contextType);
            return true;
        } else {
            LOG.trace("{} used - assuming running in the OSGi container.", contextType);
            return false;
        }
    }

}
