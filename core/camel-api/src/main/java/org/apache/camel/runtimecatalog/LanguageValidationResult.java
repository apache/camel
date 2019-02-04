/**
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
package org.apache.camel.runtimecatalog;

import java.io.Serializable;

/**
 * Validation result of parsing a language expression or predicate
 */
public class LanguageValidationResult implements Serializable {
    private final String text;
    private String error;
    private String shortError;
    private int index;

    public LanguageValidationResult(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public boolean isSuccess() {
        return error == null;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public String getShortError() {
        return shortError;
    }

    public void setShortError(String shortError) {
        this.shortError = shortError;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}
