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
package org.apache.felix.bundleplugin;

import org.apache.maven.plugin.logging.Log;

/**
 * Patched logger that is not noisy.
 */
public class PatchedLog implements Log {

    private final Log delegate;

    public PatchedLog(Log delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public void debug(CharSequence charSequence) {
        delegate.debug(charSequence);
    }

    @Override
    public void debug(CharSequence charSequence, Throwable throwable) {
        delegate.debug(charSequence, throwable);
    }

    @Override
    public void debug(Throwable throwable) {
        delegate.debug(throwable);
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public void info(CharSequence charSequence) {
        delegate.info(charSequence);
    }

    @Override
    public void info(CharSequence charSequence, Throwable throwable) {
        delegate.info(charSequence, throwable);
    }

    @Override
    public void info(Throwable throwable) {
        delegate.info(throwable);
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public void warn(CharSequence charSequence) {
        // skip some unwanted WARN logging
        String s = charSequence.toString();
        if (s.startsWith("Include-Resource: overriding")) {
            return;
        }
        delegate.warn(charSequence);
    }

    @Override
    public void warn(CharSequence charSequence, Throwable throwable) {
        delegate.warn(charSequence, throwable);
    }

    @Override
    public void warn(Throwable throwable) {
        delegate.warn(throwable);
    }

    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    @Override
    public void error(CharSequence charSequence) {
        delegate.error(charSequence);
    }

    @Override
    public void error(CharSequence charSequence, Throwable throwable) {
        delegate.error(charSequence, throwable);
    }

    @Override
    public void error(Throwable throwable) {
        delegate.error(throwable);
    }
}
