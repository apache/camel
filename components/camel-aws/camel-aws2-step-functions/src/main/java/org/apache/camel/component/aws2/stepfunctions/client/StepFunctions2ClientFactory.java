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
package org.apache.camel.component.aws2.stepfunctions.client;

import org.apache.camel.component.aws2.stepfunctions.StepFunctions2Configuration;
import org.apache.camel.component.aws2.stepfunctions.client.impl.StepFunctions2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.stepfunctions.client.impl.StepFunctions2ClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.stepfunctions.client.impl.StepFunctions2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.stepfunctions.client.impl.StepFunctions2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS StepFunctions client.
 */
public final class StepFunctions2ClientFactory {

    private StepFunctions2ClientFactory() {
    }

    /**
     * Return the correct AWS StepFunctions client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               StepFunctionsClient
     */
    public static StepFunctions2InternalClient getSfnClient(StepFunctions2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new StepFunctions2ClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new StepFunctions2ClientIAMProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new StepFunctions2ClientSessionTokenImpl(configuration);
        } else {
            return new StepFunctions2ClientStandardImpl(configuration);
        }
    }
}
