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
package org.apache.camel.catalog;

import java.io.Serializable;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Validation result of parsing a language expression or predicate
 */
public class LanguageValidationResult implements Serializable {
    private final String text;
    private @Nullable String error;
    private @Nullable String shortError;
    private int index;

    public LanguageValidationResult(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public String getText() {
        return text;
    }

    public boolean isSuccess() {
        return error == null;
    }

    public void setError(@Nullable String error) {
        this.error = error;
    }

    public @Nullable String getError() {
        return error;
    }

    public @Nullable String getShortError() {
        return shortError;
    }

    public void setShortError(@Nullable String shortError) {
        this.shortError = shortError;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}
