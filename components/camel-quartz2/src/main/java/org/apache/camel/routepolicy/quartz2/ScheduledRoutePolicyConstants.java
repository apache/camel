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
package org.apache.camel.routepolicy.quartz2;

/**
 * Quartz constants.
 */
public interface ScheduledRoutePolicyConstants {
    enum Action {
        START, STOP, SUSPEND, RESUME
    };
    
    String SCHEDULED_ROUTE = "ScheduledRoute";
    String SCHEDULED_TRIGGER = "ScheduledTrigger";
    String SCHEDULED_ACTION = "ScheduledAction";
    String JOB_START = "job-" + Action.START + "-";
    String JOB_STOP = "job-" + Action.STOP + "-";
    String JOB_SUSPEND = "job-" + Action.SUSPEND + "-";
    String JOB_RESUME = "job-" + Action.RESUME + "-";
    String JOB_GROUP = "jobGroup-";
    String TRIGGER_START = "trigger-" + Action.START + "-";
    String TRIGGER_STOP = "trigger-" + Action.STOP + "-";
    String TRIGGER_SUSPEND = "trigger-" + Action.SUSPEND + "-";
    String TRIGGER_RESUME = "trigger-" + Action.RESUME + "-";
    String TRIGGER_GROUP = "triggerGroup-";
    
}
