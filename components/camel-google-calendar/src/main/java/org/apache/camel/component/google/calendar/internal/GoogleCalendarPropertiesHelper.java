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
package org.apache.camel.component.google.calendar.internal;

import org.apache.camel.component.google.calendar.GoogleCalendarConfiguration;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

/**
 * Singleton {@link ApiMethodPropertiesHelper} for GoogleCalendar component.
 */
public final class GoogleCalendarPropertiesHelper extends ApiMethodPropertiesHelper<GoogleCalendarConfiguration> {

    private static GoogleCalendarPropertiesHelper helper;

    private GoogleCalendarPropertiesHelper() {
        super(GoogleCalendarConfiguration.class, GoogleCalendarConstants.PROPERTY_PREFIX);
    }

    public static synchronized GoogleCalendarPropertiesHelper getHelper() {
        if (helper == null) {
            helper = new GoogleCalendarPropertiesHelper();
        }
        return helper;
    }
}
