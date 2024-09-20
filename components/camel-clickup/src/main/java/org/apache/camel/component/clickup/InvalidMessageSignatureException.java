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
package org.apache.camel.component.clickup;

public class InvalidMessageSignatureException extends RuntimeException {

    private final String message;
    private final String messageSignature;
    private final String calculatedSignature;

    public InvalidMessageSignatureException(String message, String messageSignature, String calculatedSignature) {
        this.message = message;
        this.messageSignature = messageSignature;
        this.calculatedSignature = calculatedSignature;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getMessageSignature() {
        return messageSignature;
    }

    public String getCalculatedSignature() {
        return calculatedSignature;
    }

}
