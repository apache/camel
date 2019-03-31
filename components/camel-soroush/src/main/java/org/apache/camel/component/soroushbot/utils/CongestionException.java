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

public class CongestionException extends RuntimeException {
    private SoroushMessage body;

    public CongestionException() {
    }

    public CongestionException(String message) {
        super(message);
    }

    public CongestionException(String message, Throwable cause) {
        super(message, cause);
    }

    public CongestionException(Throwable cause) {
        super(cause);
    }

    public CongestionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public CongestionException(SoroushMessage body) {
        this.body = body;
    }

    public CongestionException(String message, SoroushMessage body) {
        super(message);
        this.body = body;
    }

    public CongestionException(String message, Throwable cause, SoroushMessage body) {
        super(message, cause);
        this.body = body;
    }

    public CongestionException(Throwable cause, SoroushMessage body) {
        super(cause);
        this.body = body;
    }

    public CongestionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, SoroushMessage body) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.body = body;
    }

    public SoroushMessage getBody() {
        return body;
    }

    public void setBody(SoroushMessage body) {
        this.body = body;
    }
}
