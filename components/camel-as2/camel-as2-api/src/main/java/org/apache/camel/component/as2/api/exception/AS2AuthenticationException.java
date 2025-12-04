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

package org.apache.camel.component.as2.api.exception;

import org.apache.camel.component.as2.api.entity.AS2DispositionModifier;

/**
 * Thrown when an AS2 signature fails validation.
 */
public class AS2AuthenticationException extends AS2ErrorDispositionException {

    public AS2AuthenticationException(String message) {
        super(message);
    }

    @Override
    public AS2DispositionModifier getDispositionModifier() {
        return AS2DispositionModifier.ERROR_AUTHENTICATION_FAILED;
    }
}
