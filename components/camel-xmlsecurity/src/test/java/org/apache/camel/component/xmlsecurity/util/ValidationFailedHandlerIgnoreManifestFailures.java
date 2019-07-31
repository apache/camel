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
package org.apache.camel.component.xmlsecurity.util;

import javax.xml.crypto.dsig.Reference;

import org.apache.camel.component.xmlsecurity.api.DefaultValidationFailedHandler;

/**
 * Validation failed handler which ignores manifest reference validation
 * failures.
 */
public class ValidationFailedHandlerIgnoreManifestFailures extends DefaultValidationFailedHandler {

    @Override
    public void manifestReferenceValidationFailed(Reference ref) throws Exception {
        // do nothing
    }

    @Override
    public boolean ignoreCoreValidationFailure() throws Exception {
        return true;
    }
}
