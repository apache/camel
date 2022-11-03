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
import org.apache.camel.resume.ResumeStrategyConfiguration;

public class WriteAheadResumeStrategyConfiguration extends ResumeStrategyConfiguration {
    public static final long DEFAULT_SUPERVISOR_INTERVAL = 100;

    private File logFile;
    private ResumeStrategy delegateResumeStrategy;
    private long supervisorInterval;

    public File getLogFile() {
        return logFile;
    }

    void setLogFile(File logFile) {
        this.logFile = logFile;
    }

    public ResumeStrategy getDelegateResumeStrategy() {
        return delegateResumeStrategy;
    }

    void setDelegateResumeStrategy(ResumeStrategy delegateResumeStrategy) {
        this.delegateResumeStrategy = delegateResumeStrategy;
    }

    public long getSupervisorInterval() {
        return supervisorInterval;
    }

    void setSupervisorInterval(long supervisorInterval) {
        this.supervisorInterval = supervisorInterval;
    }

    @Override
    public String resumeStrategyService() {
        return "write-ahead-resume-strategy";
    }
}
