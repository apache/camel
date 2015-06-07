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
package org.apache.camel.component.aws.swf;

import com.amazonaws.services.simpleworkflow.flow.DynamicActivitiesClient;
import com.amazonaws.services.simpleworkflow.flow.DynamicActivitiesClientImpl;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.model.ActivityType;

public class CamelSWFActivityClient {
    private final DynamicActivitiesClient dynamicActivitiesClient;
    private SWFConfiguration configuration;

    public CamelSWFActivityClient(SWFConfiguration configuration) {
        this.configuration = configuration;
        dynamicActivitiesClient = getDynamicActivitiesClient();
    }

    public Object scheduleActivity(String eventName, String version, Object input) {
        ActivityType activity = new ActivityType();
        activity.setName(eventName);
        activity.setVersion(version);

        Promise<?>[] promises = asPromiseArray(input);
        Promise<?> promise = dynamicActivitiesClient.scheduleActivity(activity, promises, configuration.getActivitySchedulingOptions(), Object.class, null);
        return promise;
    }

    protected Promise<?>[] asPromiseArray(Object input) {
        Promise<?>[] promises;
        if (input instanceof Object[]) {
            Object[] inputArray = (Object[])input;
            promises = new Promise[inputArray.length];
            for (int i = 0; i < inputArray.length; i++) {
                promises[i] = Promise.asPromise(inputArray[i]);
            }
        } else {
            promises = new Promise[1];
            if (input instanceof Promise) {
                promises[0] = (Promise<?>) input;
            } else {
                promises[0] = Promise.asPromise(input);
            }
        }
        return promises;
    }

    DynamicActivitiesClient getDynamicActivitiesClient() {
        return new DynamicActivitiesClientImpl(null, configuration.getDataConverter());
    }

}
