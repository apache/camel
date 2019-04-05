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

package org.apache.camel.component.soroushbot.utils;

import org.apache.camel.component.soroushbot.models.SoroushMessage;

public class MaximumConnectionRetryReachedException extends RuntimeException {
    SoroushMessage soroushMessage;

    public MaximumConnectionRetryReachedException(SoroushMessage soroushMessage) {
        this.soroushMessage = soroushMessage;
    }

    public MaximumConnectionRetryReachedException(String message, SoroushMessage soroushMessage) {
        super(message);
        this.soroushMessage = soroushMessage;
    }

    public MaximumConnectionRetryReachedException(String message, Throwable cause, SoroushMessage soroushMessage) {
        super(message, cause);
        this.soroushMessage = soroushMessage;
    }

    public MaximumConnectionRetryReachedException(Throwable cause, SoroushMessage soroushMessage) {
        super(cause);
        this.soroushMessage = soroushMessage;
    }

    public MaximumConnectionRetryReachedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, SoroushMessage soroushMessage) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.soroushMessage = soroushMessage;
    }
    public MaximumConnectionRetryReachedException() {
    }

    public MaximumConnectionRetryReachedException(String message) {
        super(message);
    }

    public MaximumConnectionRetryReachedException(String message, Throwable cause) {
        super(message, cause);
    }

    public MaximumConnectionRetryReachedException(Throwable cause) {
        super(cause);
    }

    public MaximumConnectionRetryReachedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    @Override
    public String getLocalizedMessage() {
        return "MaximumConnectionRetryReachedException{" +
                "soroushMessage=" + soroushMessage +
                ", message=" + getMessage() +
                "} ";
    }
}
