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
package org.apache.camel.routepolicy.quartz;

import org.quartz.JobDetail;
import org.quartz.Trigger;

public class ScheduledRouteDetails {
    private JobDetail startJobDetail;
    private JobDetail stopJobDetail;
    private JobDetail suspendJobDetail;
    private JobDetail resumeJobDetail;
    private Trigger startTrigger;
    private Trigger stopTrigger;
    private Trigger suspendTrigger;
    private Trigger resumeTrigger;

    public JobDetail getStartJobDetail() {
        return startJobDetail;
    }

    public void setStartJobDetail(JobDetail startJobDetail) {
        this.startJobDetail = startJobDetail;
    }

    public JobDetail getStopJobDetail() {
        return stopJobDetail;
    }

    public void setStopJobDetail(JobDetail stopJobDetail) {
        this.stopJobDetail = stopJobDetail;
    }

    public JobDetail getSuspendJobDetail() {
        return suspendJobDetail;
    }

    public void setSuspendJobDetail(JobDetail suspendJobDetail) {
        this.suspendJobDetail = suspendJobDetail;
    }

    public Trigger getStartTrigger() {
        return startTrigger;
    }

    public void setStartTrigger(Trigger startTrigger) {
        this.startTrigger = startTrigger;
    }

    public Trigger getStopTrigger() {
        return stopTrigger;
    }

    public void setStopTrigger(Trigger stopTrigger) {
        this.stopTrigger = stopTrigger;
    }

    public Trigger getSuspendTrigger() {
        return suspendTrigger;
    }

    public void setSuspendTrigger(Trigger suspendTrigger) {
        this.suspendTrigger = suspendTrigger;
    }

    public void setResumeJobDetail(JobDetail resumeJobDetail) {
        this.resumeJobDetail = resumeJobDetail;
    }

    public JobDetail getResumeJobDetail() {
        return resumeJobDetail;
    }

    public void setResumeTrigger(Trigger resumeTrigger) {
        this.resumeTrigger = resumeTrigger;
    }

    public Trigger getResumeTrigger() {
        return resumeTrigger;
    }
    
}
