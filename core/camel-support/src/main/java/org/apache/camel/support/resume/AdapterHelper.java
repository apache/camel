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

package org.apache.camel.support.resume;

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.FactoryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

public final class AdapterHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AdapterHelper.class);

    private AdapterHelper() {
    }

    public static ResumeAdapter eval(CamelContext context, ResumeAware<?> resumeAware, ResumeStrategy resumeStrategy) {
        assert context != null;
        assert resumeAware != null;
        assert resumeStrategy != null;

        LOG.debug("Using the factory finder to search for the resume adapter");
        final FactoryFinder factoryFinder
                = context.getCamelContextExtension().getFactoryFinder(FactoryFinder.DEFAULT_PATH);

        LOG.debug("Creating a new resume adapter");
        Optional<ResumeAdapter> adapterOptional
                = factoryFinder.newInstance(resumeAware.adapterFactoryService(), ResumeAdapter.class);

        if (adapterOptional.isEmpty()) {
            throw new RuntimeCamelException("Cannot find a resume adapter class in the consumer classpath or in the registry");
        }

        final ResumeAdapter resumeAdapter = adapterOptional.get();
        LOG.debug("Using the acquired resume adapter: {}", resumeAdapter.getClass().getName());

        if (resumeAdapter instanceof Cacheable cacheableAdapter) {
            final ResumeStrategyConfiguration resumeStrategyConfiguration = resumeStrategy.getResumeStrategyConfiguration();

            final ResumeCache<?> resumeCache = resumeStrategyConfiguration.getResumeCache();
            if (resumeStrategyConfiguration != null && resumeCache != null) {
                cacheableAdapter.setCache(resumeCache);
            } else {
                LOG.error("No cache was provided in the configuration for the cacheable resume adapter {}",
                        resumeAdapter.getClass().getName());
                throw new RuntimeCamelException(
                        format("No cache was provided in the configuration for the cacheable resume adapter %s",
                                resumeAdapter.getClass().getName()));
            }
        } else {
            LOG.debug("The resume adapter {} is not cacheable", resumeAdapter.getClass().getName());
        }

        return resumeAdapter;
    }
}
