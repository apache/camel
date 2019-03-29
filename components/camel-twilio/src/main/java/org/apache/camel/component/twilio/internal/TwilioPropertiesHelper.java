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
package org.apache.camel.component.twilio.internal;

import org.apache.camel.component.twilio.TwilioConfiguration;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;

/**
 * Singleton {@link ApiMethodPropertiesHelper} for Twilio component.
 */
public final class TwilioPropertiesHelper extends ApiMethodPropertiesHelper<TwilioConfiguration> {

    private static TwilioPropertiesHelper helper;

    private TwilioPropertiesHelper() {
        super(TwilioConfiguration.class, TwilioConstants.PROPERTY_PREFIX);
    }

    public static synchronized TwilioPropertiesHelper getHelper() {
        if (helper == null) {
            helper = new TwilioPropertiesHelper();
        }
        return helper;
    }
}
