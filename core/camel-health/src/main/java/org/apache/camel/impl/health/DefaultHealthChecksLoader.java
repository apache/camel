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
package org.apache.camel.impl.health;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To load custom health-checks by classpath scanning.
 */
public class DefaultHealthChecksLoader {

    public static final String META_INF_SERVICES = "META-INF/services/org/apache/camel/health-check";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHealthChecksLoader.class);
    private static final Charset UTF8 = Charset.forName("UTF-8");

    protected PackageScanResourceResolver resolver;
    protected Set<Class<?>> visitedClasses = new HashSet<>();
    protected Set<String> visitedURIs = new HashSet<>();

    public DefaultHealthChecksLoader(PackageScanResourceResolver resolver) {
        this.resolver = resolver;
    }

    public Collection<HealthCheck> loadHealthChecks() {
        Collection<HealthCheck> answer = new ArrayList<>();

        LOG.trace("Searching for {} health checks", META_INF_SERVICES);

        try {
            Collection<Resource> resources = resolver.findResources(META_INF_SERVICES + "/*-check");
            for (Resource resource : resources) {
                System.out.println(resource);
            }
        } catch (Exception e) {
            // ignore
        }

        return answer;
    }

}
