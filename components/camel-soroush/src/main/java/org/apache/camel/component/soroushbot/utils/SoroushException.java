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

import org.apache.camel.component.soroushbot.models.response.SoroushResponse;

/**
 * Exception representation of response from Soroush server.
 */
public class SoroushException extends Exception {
    SoroushResponse soroushResponse;
    Integer status;
    String body;

    public SoroushException() {
    }

    public SoroushException(SoroushResponse soroushResponse, Integer status, String body) {
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.body = body;
    }

    public SoroushException(String message, SoroushResponse soroushResponse, Integer status, String body) {
        super(message);
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.body = body;
    }

    public SoroushException(String message, Throwable cause, SoroushResponse soroushResponse, Integer status, String body) {
        super(message, cause);
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.body = body;
    }

    public SoroushException(Throwable cause, SoroushResponse soroushResponse, Integer status, String body) {
        super(cause);
        this.soroushResponse = soroushResponse;
        this.status = status;
        this.body = body;
    }

    public SoroushException(Integer status, String body) {
        this.status = status;
        this.body = body;
    }

    public SoroushException(String message, Integer status, String body) {
        super(message);
        this.status = status;
        this.body = body;
    }

    public SoroushException(String message, Throwable cause, Integer status, String body) {
        super(message, cause);
        this.status = status;
        this.body = body;
    }

    public SoroushException(Throwable cause, Integer status, String body) {
        super(cause);
        this.status = status;
        this.body = body;
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
                "soroushResponse=" + soroushResponse +
                ", status=" + status +
                ", body='" + body + '\'' +
                ", message='" + getMessage() + '\'' +
                "} ";
    }

}
