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
package org.apache.camel.bam;

import org.apache.camel.bam.ActivityRules;
import org.apache.camel.bam.model.ActivityState;

import java.util.List;
import java.util.ArrayList;

/**
 * @version $Revision: $
 */
public class ProcessRules {
    private List<ActivityRules> activities = new ArrayList<ActivityRules>();


    public List<ActivityRules> getActivities() {
        return activities;
    }

    public void processExpired(ActivityState activityState) throws Exception {
        for (ActivityRules activityRules : activities) {
            activityRules.processExpired(activityState);
        }
    }
}
