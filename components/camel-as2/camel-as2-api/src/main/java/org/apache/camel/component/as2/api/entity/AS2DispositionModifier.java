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
package org.apache.camel.component.as2.api.entity;

public final class AS2DispositionModifier {

    public static final AS2DispositionModifier ERROR = new AS2DispositionModifier("error");
    public static final AS2DispositionModifier ERROR_AUTHENTICATION_FAILED = new AS2DispositionModifier("error: authentication-failed");
    public static final AS2DispositionModifier ERROR_DECOMPRESSION_FAILED = new AS2DispositionModifier("error: decompression-failed");
    public static final AS2DispositionModifier ERROR_DECRYPTION_FAILED = new AS2DispositionModifier("error: decryption-failed");
    public static final AS2DispositionModifier ERROR_INSUFFICIENT_MESSAGE_SECURITY = new AS2DispositionModifier("error: insufficient-message-security");
    public static final AS2DispositionModifier ERROR_INTEGRITY_CHECK_FAILED = new AS2DispositionModifier("error: integrity-check-failed");
    public static final AS2DispositionModifier ERROR_UNEXPECTED_PROCESSING_ERROR = new AS2DispositionModifier("error: unexpected-processing-error");
    public static final AS2DispositionModifier WARNING = new AS2DispositionModifier("warning");

    private String modifier;

    private AS2DispositionModifier(String modifier) {
        this.modifier = modifier;
    }

    public String getModifier() {
        return modifier;
    }

    public boolean isError() {
        return modifier.startsWith("error: ");
    }

    public boolean isFailuer() {
        return modifier.startsWith("failure: ");
    }

    public boolean isWarning() {
        return modifier.startsWith("warning: ");
    }

    @Override
    public String toString() {
        return modifier;
    }

    public static AS2DispositionModifier createWarning(String description) {
        return new AS2DispositionModifier("warning: " + description);
    }

    public static AS2DispositionModifier createFailure(String description) {
        return new AS2DispositionModifier("failure: " + description);
    }

    public static AS2DispositionModifier parseDispositionType(String dispositionModifierString) {
        switch(dispositionModifierString) {
            case "error":
                return ERROR;
            case "error: authentication-failed":
                return ERROR_AUTHENTICATION_FAILED;
            case "error: decompression-failed\"":
                return ERROR_DECOMPRESSION_FAILED;
            case "error: decryption-failed":
                return ERROR_DECRYPTION_FAILED;
            case "error: insufficient-message-security":
                return ERROR_INSUFFICIENT_MESSAGE_SECURITY;
            case "error: integrity-check-failed":
                return ERROR_INTEGRITY_CHECK_FAILED;
            case "error: unexpected-processing-error":
                return ERROR_UNEXPECTED_PROCESSING_ERROR;
            case "warning":
                return WARNING;
            default:
                if (dispositionModifierString.startsWith("warning: ") || dispositionModifierString.startsWith("failure: ")) {
                    return new AS2DispositionModifier(dispositionModifierString);
                }
                return null;
        }
    }

}
