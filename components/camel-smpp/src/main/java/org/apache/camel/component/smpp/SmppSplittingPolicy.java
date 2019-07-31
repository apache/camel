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
package org.apache.camel.component.smpp;

public enum SmppSplittingPolicy {

    ALLOW,
    REJECT,
    TRUNCATE;

    public static SmppSplittingPolicy fromString(String policyName) throws SmppException {

        if (policyName == null) {
            throw new SmppException("policyName must not be null");
        }

        for (SmppSplittingPolicy nextPolicy : values()) {
            if (nextPolicy.name().equals(policyName)) {
                return nextPolicy;
            }
        }

        throw new SmppException("Unrecognised SmppSplittingPolicy: " + policyName);
    }

}
