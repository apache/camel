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

package org.apache.camel.component.wal;

import java.io.File;

import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.resume.BasicResumeStrategyConfigurationBuilder;

public class WriteAheadResumeStrategyConfigurationBuilder
        extends
        BasicResumeStrategyConfigurationBuilder<WriteAheadResumeStrategyConfigurationBuilder, WriteAheadResumeStrategyConfiguration> {
    private File logFile;
    private ResumeStrategy delegateResumeStrategy;
    private long supervisorInterval;

    /**
     * The transaction log file to use
     *
     * @param  logFile the file
     * @return         this instance
     */
    public WriteAheadResumeStrategyConfigurationBuilder withLogFile(File logFile) {
        this.logFile = logFile;

        return this;
    }

    public WriteAheadResumeStrategyConfigurationBuilder withDelegateResumeStrategy(ResumeStrategy delegateResumeStrategy) {
        this.delegateResumeStrategy = delegateResumeStrategy;

        return this;
    }

    public WriteAheadResumeStrategyConfigurationBuilder withSupervisorInterval(long supervisorInterval) {
        this.supervisorInterval = supervisorInterval;

        return this;
    }

    @Override
    public WriteAheadResumeStrategyConfiguration build() {
        final WriteAheadResumeStrategyConfiguration writeAheadResumeStrategyConfiguration
                = new WriteAheadResumeStrategyConfiguration();

        buildCommonConfiguration(writeAheadResumeStrategyConfiguration);

        writeAheadResumeStrategyConfiguration.setLogFile(logFile);
        writeAheadResumeStrategyConfiguration.setDelegateResumeStrategy(delegateResumeStrategy);
        writeAheadResumeStrategyConfiguration.setSupervisorInterval(supervisorInterval);

        return writeAheadResumeStrategyConfiguration;
    }

    /**
     * Creates an empty builder
     *
     * @return an empty configuration builder
     */
    public static WriteAheadResumeStrategyConfigurationBuilder newEmptyBuilder() {
        return new WriteAheadResumeStrategyConfigurationBuilder();
    }

    /**
     * Creates the most basic builder possible
     *
     * @return a pre-configured basic builder
     */
    public static WriteAheadResumeStrategyConfigurationBuilder newBuilder() {
        WriteAheadResumeStrategyConfigurationBuilder builder = new WriteAheadResumeStrategyConfigurationBuilder();

        builder.withSupervisorInterval(WriteAheadResumeStrategyConfiguration.DEFAULT_SUPERVISOR_INTERVAL);

        return builder;
    }
}
