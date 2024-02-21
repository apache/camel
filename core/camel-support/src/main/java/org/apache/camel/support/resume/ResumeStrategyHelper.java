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

import org.apache.camel.CamelContext;
import org.apache.camel.resume.ResumeAction;
import org.apache.camel.resume.ResumeActionAware;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for the resume strategy
 */
public final class ResumeStrategyHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ResumeStrategyHelper.class);

    private ResumeStrategyHelper() {

    }

    /**
     * Executes the resume operation
     *
     * @param  context        a camel context on which the registry will be searched for the resume action
     * @param  on             the calling instance for the resume operation
     * @param  resumeStrategy the instance of the {@link ResumeStrategy} to perform the resume
     * @param  actionName     an action name that maps to a {@link ResumeAction} object in the registry
     * @throws Exception      if the strategy is unable to load the cache
     */
    public static void resume(
            CamelContext context, Object on, ResumeStrategy resumeStrategy,
            String actionName)
            throws Exception {
        resume(context, on, resumeStrategy, actionName, ResumeAdapter.class);
    }

    /**
     * Executes the resume operation
     *
     * @param  context        a camel context on which the registry will be searched for the resume action
     * @param  on             the calling instance for the resume operation
     * @param  resumeStrategy the instance of the {@link ResumeStrategy} to perform the resume
     * @param  actionName     an action name that maps to a {@link ResumeAction} object in the registry
     * @param  adapterClass   the class of the {@link ResumeAdapter} to look for in the registry
     * @throws Exception      if the strategy is unable to load the cache
     */
    public static <T extends ResumeAdapter> void resume(
            CamelContext context, Object on, ResumeStrategy resumeStrategy,
            String actionName, Class<T> adapterClass)
            throws Exception {
        if (resumeStrategy == null) {
            LOG.debug("Skipping resume operation because there's no resume strategy defined");

            return;
        }

        LOG.debug("Loading the resume cache");
        resumeStrategy.loadCache();

        T resumeAdapter = resumeStrategy.getAdapter(adapterClass);
        if (resumeAdapter == null) {
            LOG.warn("The resume cannot be executed because no resume adapter was provided");

            return;
        }

        if (resumeAdapter instanceof ResumeActionAware) {
            ResumeAction action = (ResumeAction) context.getRegistry().lookupByName(actionName);
            ObjectHelper.notNull(action, "The resume action cannot be null", on);

            ((ResumeActionAware) resumeAdapter).setResumeAction(action);
        }

        resumeAdapter.resume();
    }
}
