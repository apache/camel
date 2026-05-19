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
package org.apache.camel;

import java.util.Collections;
import java.util.List;

/**
 * Result of a Splitter EIP operation that provides structured information about the outcome, including failure details
 * when error thresholds ({@code errorThreshold} or {@code maxFailedRecords}) are configured.
 * <p/>
 * This result is available as an exchange property ({@link Exchange#SPLIT_RESULT}) after the split operation completes.
 */
public final class SplitResult {

    /**
     * Details of a single split item failure.
     *
     * @param index     the 0-based index of the failed item in the split sequence
     * @param exception the exception that caused the failure
     */
    public record Failure(int index, Exception exception) {
    }

    private final int totalItems;
    private final int failureCount;
    private final List<Failure> failures;
    private final boolean aborted;

    public SplitResult(int totalItems, int failureCount, List<Failure> failures, boolean aborted) {
        this.totalItems = totalItems;
        this.failureCount = failureCount;
        this.failures = failures != null ? Collections.unmodifiableList(failures) : Collections.emptyList();
        this.aborted = aborted;
    }

    /**
     * The total number of items that were prepared for splitting. When {@code group()} is used, this counts the number
     * of chunks (groups), not the number of individual elements within them.
     */
    public int getTotalItems() {
        return totalItems;
    }

    /**
     * The number of items that completed successfully.
     */
    public int getSuccessCount() {
        return totalItems - failureCount;
    }

    /**
     * The number of items that failed during processing.
     */
    public int getFailureCount() {
        return failureCount;
    }

    /**
     * The list of individual failures with their index and exception details.
     */
    public List<Failure> getFailures() {
        return failures;
    }

    /**
     * Whether the split operation was aborted early because an error threshold was exceeded.
     */
    public boolean isAborted() {
        return aborted;
    }

    @Override
    public String toString() {
        return "SplitResult[total=" + totalItems
               + ", success=" + getSuccessCount()
               + ", failures=" + failureCount
               + ", aborted=" + aborted + "]";
    }
}
