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
package org.apache.camel.component.telegram;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.HttpResponseAware;

public class TelegramException extends RuntimeCamelException implements HttpResponseAware {

    private int httpResponseCode;
    private String httpResponseStatus;

    public TelegramException(String message) {
        super(message);
    }

    public TelegramException(String message, Throwable cause) {
        super(message, cause);
    }

    public TelegramException(Throwable cause) {
        super(cause);
    }

    @Override
    public int getHttpResponseCode() {
        return httpResponseCode;
    }

    @Override
    public void setHttpResponseCode(int httpResponseCode) {
        this.httpResponseCode = httpResponseCode;
    }

    @Override
    public String getHttpResponseStatus() {
        return httpResponseStatus;
    }

    @Override
    public void setHttpResponseStatus(String httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
    }
}
