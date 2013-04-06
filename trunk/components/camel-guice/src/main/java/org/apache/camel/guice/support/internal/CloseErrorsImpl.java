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
package org.apache.camel.guice.support.internal;

import com.google.inject.internal.Errors;
import com.google.inject.spi.Message;

import org.apache.camel.guice.support.CloseErrors;
import org.apache.camel.guice.support.CloseFailedException;

/**
 * The default implementation of @{link CloseErrors}
 * 
 * @version
 */
public class CloseErrorsImpl implements CloseErrors {
    final Errors errors;

    public CloseErrorsImpl(Object source) {
        this.errors = new Errors(source);
    }

    public void closeError(Object key, Object object, Exception cause) {
        String message = Errors.format("Failed to close object %s with key %s",
                object, key);
        errors.addMessage(new Message(errors.getSources(), message, cause));
    }

    public void throwIfNecessary() throws CloseFailedException {
        if (!errors.hasErrors()) {
            return;
        }

        throw new CloseFailedException(errors.getMessages());
    }
}
