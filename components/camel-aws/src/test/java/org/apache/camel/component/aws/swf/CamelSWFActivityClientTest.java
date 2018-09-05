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
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.model.ActivityType;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CamelSWFActivityClientTest {

    private DynamicActivitiesClient actClient;
    private CamelSWFActivityClient camelSWFActivityClient;

    @Before
    public void setUp() throws Exception {
        actClient = mock(DynamicActivitiesClient.class);
        camelSWFActivityClient = new CamelSWFActivityClient(new SWFConfiguration()) {
            @Override
            DynamicActivitiesClient getDynamicActivitiesClient() {
                return actClient;
            }
        };
    }

    @Test
    public void testScheduleActivity() throws Exception {
        camelSWFActivityClient.scheduleActivity("eventName", "version", "input");

        verify(actClient).scheduleActivity(any(ActivityType.class), ArgumentMatchers.<Promise<?>[]> any(), ArgumentMatchers.isNull(), ArgumentMatchers.<Class<?>> any(), ArgumentMatchers.isNull());
    }
}
