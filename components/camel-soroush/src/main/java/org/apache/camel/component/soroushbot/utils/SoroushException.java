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
import org.apache.camel.component.soroushbot.models.response.SoroushResponse;

/**
 * Exception representation of response from Soroush server.
 */
public class SoroushException extends Exception {
    SoroushMessage soroushMessage;
    SoroushResponse soroushResponse;
    Integer status;
    String responseBody;

    public SoroushException(SoroushMessage soroushMessage, SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(responseBody);
        this.soroushMessage = soroushMessage;
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(String message, SoroushMessage soroushMessage, SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(message);
        this.soroushMessage = soroushMessage;
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(String message, Throwable cause, SoroushMessage soroushMessage, SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(message, cause);
        this.soroushMessage = soroushMessage;
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(Throwable cause, SoroushMessage soroushMessage, SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(responseBody, cause);
        this.soroushMessage = soroushMessage;
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, SoroushMessage soroushMessage, SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.soroushMessage = soroushMessage;
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException() {
    }

    public SoroushException(SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(responseBody);
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(String message, SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(message);
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(String message, Throwable cause, SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(message, cause);
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(Throwable cause, SoroushResponse soroushResponse, Integer status, String responseBody) {
        super(responseBody, cause);
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(Integer status, String responseBody) {
        super(responseBody);
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(String message, Integer status, String responseBody) {
        super(message);
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(String message, Throwable cause, Integer status, String responseBody) {
        super(message, cause);
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(Throwable cause, Integer status, String responseBody) {
        super(responseBody, cause);
        this.status = status;
        this.responseBody = responseBody;
    }

    public SoroushException(String message) {
        super(message);
    }

    public SoroushException(String message, Throwable cause) {
        super(message, cause);
    }

    public SoroushException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getLocalizedMessage() {
        return "SoroushException{" +
                "soroushMessage=" + soroushMessage +
                "soroushResponse=" + soroushResponse +
                ", status=" + status +
                ", responseBody='" + responseBody + '\'' +
                ", message='" + getMessage() + '\'' +
                "} ";
    }

}
