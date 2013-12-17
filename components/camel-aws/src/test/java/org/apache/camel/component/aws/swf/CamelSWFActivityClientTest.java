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

import com.amazonaws.services.simpleworkflow.flow.ActivitySchedulingOptions;
import com.amazonaws.services.simpleworkflow.flow.DynamicActivitiesClient;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.model.ActivityType;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CamelSWFActivityClientTest {

    private DynamicActivitiesClient activitiesClient;
    private CamelSWFActivityClient camelSWFActivityClient;

    @Before
    public void setUp() throws Exception {
        activitiesClient = mock(DynamicActivitiesClient.class);
        camelSWFActivityClient = new CamelSWFActivityClient(new SWFConfiguration()) {
            @Override
            DynamicActivitiesClient getDynamicActivitiesClient() {
                return activitiesClient;
            }
        };
    }

    @Test
    public void testScheduleActivity() throws Exception {
        Object result = camelSWFActivityClient.scheduleActivity("eventName", "version", "input");
        verify(activitiesClient).scheduleActivity(any(ActivityType.class),  any(Promise[].class), isNull(ActivitySchedulingOptions.class), any(Class.class), isNull(Promise.class));
    }
}
