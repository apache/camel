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
package org.apache.camel.component.aws.common;

import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * Helpers for working with {@link AwsServiceException} instances.
 */
public final class AwsExceptionUtil {

    private AwsExceptionUtil() {
    }

    /**
     * Returns the AWS error code of the given exception, or {@code null} when it is not available.
     * <p>
     * {@link AwsServiceException#awsErrorDetails()} is nullable, so calling {@code e.awsErrorDetails().errorCode()}
     * directly can throw a {@link NullPointerException} - and, because it is typically used as a method argument (for
     * example when logging), the expression is evaluated eagerly regardless of the log level. This helper reads the
     * error code defensively so a null {@code awsErrorDetails} does not mask the original exception.
     *
     * @param  exception the exception, may be {@code null}
     * @return           the error code, or {@code null} if the exception or its error details are absent
     */
    public static String errorCode(AwsServiceException exception) {
        if (exception == null || exception.awsErrorDetails() == null) {
            return null;
        }
        return exception.awsErrorDetails().errorCode();
    }
}
