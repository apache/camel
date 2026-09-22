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
package org.apache.camel.test.infra.openai.mock;

/**
 * The reply the mock writes for one request line of a batch input file, selected by its {@code custom_id}.
 * <p>
 * A line with a successful reply is written to the output file of the batch, a line with an error to its error file, as
 * the Batch API does.
 */
public class BatchExpectation {

    private final String customId;
    private String responseBody;
    private int errorStatusCode;
    private String errorType;
    private String errorMessage;

    public BatchExpectation(String customId) {
        this.customId = customId;
    }

    public String getCustomId() {
        return customId;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public void setError(int statusCode, String type, String message) {
        this.errorStatusCode = statusCode;
        this.errorType = type;
        this.errorMessage = message;
    }

    public boolean hasError() {
        return errorStatusCode > 0;
    }

    public int getErrorStatusCode() {
        return errorStatusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean matches(String customId) {
        return this.customId.equals(customId);
    }
}
