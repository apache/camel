/**
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
package org.apache.camel.routepolicy.quartz2;

import org.quartz.JobKey;
import org.quartz.TriggerKey;

public class ScheduledRouteDetails {
    private JobKey startJobKey;
    private JobKey stopJobKey;
    private JobKey suspendJobKey;
    private JobKey resumeJobKey;
    private TriggerKey startTriggerKey;
    private TriggerKey stopTriggerKey;
    private TriggerKey suspendTriggerKey;
    private TriggerKey resumeTriggerKey;

    public JobKey getStartJobKey() {
        return startJobKey;
    }

    public void setStartJobKey(JobKey startJobKey) {
        this.startJobKey = startJobKey;
    }

    public JobKey getStopJobKey() {
        return stopJobKey;
    }

    public void setStopJobKey(JobKey stopJobKey) {
        this.stopJobKey = stopJobKey;
    }

    public JobKey getSuspendJobKey() {
        return suspendJobKey;
    }

    public void setSuspendJobKey(JobKey suspendJobKey) {
        this.suspendJobKey = suspendJobKey;
    }

    public JobKey getResumeJobKey() {
        return resumeJobKey;
    }

    public void setResumeJobKey(JobKey resumeJobKey) {
        this.resumeJobKey = resumeJobKey;
    }

    public TriggerKey getStartTriggerKey() {
        return startTriggerKey;
    }

    public void setStartTriggerKey(TriggerKey startTriggerKey) {
        this.startTriggerKey = startTriggerKey;
    }

    public TriggerKey getStopTriggerKey() {
        return stopTriggerKey;
    }

    public void setStopTriggerKey(TriggerKey stopTriggerKey) {
        this.stopTriggerKey = stopTriggerKey;
    }

    public TriggerKey getSuspendTriggerKey() {
        return suspendTriggerKey;
    }

    public void setSuspendTriggerKey(TriggerKey suspendTriggerKey) {
        this.suspendTriggerKey = suspendTriggerKey;
    }

    public TriggerKey getResumeTriggerKey() {
        return resumeTriggerKey;
    }

    public void setResumeTriggerKey(TriggerKey resumeTriggerKey) {
        this.resumeTriggerKey = resumeTriggerKey;
    }
}
