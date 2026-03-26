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
package org.apache.camel.component.bulk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a bulk processing job containing statistics and failure details.
 */
public class BulkResult {

    private final String jobName;
    private final String jobInstanceId;
    private final int totalItems;
    private final int successCount;
    private final int failureCount;
    private final long duration;
    private final boolean aborted;
    private final List<BulkFailure> failures;

    public BulkResult(String jobName, String jobInstanceId, int totalItems, int successCount, int failureCount,
                      long duration, boolean aborted, List<BulkFailure> failures) {
        this.jobName = jobName;
        this.jobInstanceId = jobInstanceId;
        this.totalItems = totalItems;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.duration = duration;
        this.aborted = aborted;
        this.failures = failures != null ? Collections.unmodifiableList(new ArrayList<>(failures)) : Collections.emptyList();
    }

    public String getJobName() {
        return jobName;
    }

    public String getJobInstanceId() {
        return jobInstanceId;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isAborted() {
        return aborted;
    }

    public List<BulkFailure> getFailures() {
        return failures;
    }

    @Override
    public String toString() {
        return "BulkResult[job=" + jobName
               + ", instanceId=" + jobInstanceId
               + ", total=" + totalItems
               + ", success=" + successCount
               + ", failed=" + failureCount
               + ", duration=" + duration + "ms"
               + ", aborted=" + aborted + "]";
    }

    /**
     * Represents a single item failure within a bulk job.
     */
    public static class BulkFailure {

        private final int index;
        private final Object item;
        private final Throwable cause;
        private final int stepIndex;

        public BulkFailure(int index, Object item, Throwable cause, int stepIndex) {
            this.index = index;
            this.item = item;
            this.cause = cause;
            this.stepIndex = stepIndex;
        }

        public int getIndex() {
            return index;
        }

        public Object getItem() {
            return item;
        }

        public Throwable getCause() {
            return cause;
        }

        public int getStepIndex() {
            return stepIndex;
        }

        @Override
        public String toString() {
            return "BulkFailure[index=" + index + ", step=" + stepIndex + ", cause=" + cause.getMessage() + "]";
        }
    }
}
