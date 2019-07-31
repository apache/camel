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
package org.apache.camel.component.aws.swf;

public interface SWFConstants {
    String OPERATION = "CamelSWFOperation";
    String WORKFLOW_ID = "CamelSWFWorkflowId";
    String RUN_ID = "CamelSWFRunId";
    String STATE_RESULT_TYPE = "CamelSWFStateResultType";
    String EVENT_NAME = "CamelSWFEventName";
    String VERSION = "CamelSWFVersion";
    String TAGS = "CamelSWFTags";
    String SIGNAL_NAME = "CamelSWFSignalName";
    String CHILD_POLICY = "CamelSWFChildPolicy";
    String DETAILS = "CamelSWFDetails";
    String REASON = "CamelSWFReason";
    String ACTION = "CamelSWFAction";
    String EXECUTE_ACTION = "CamelSWFActionExecute";
    String SIGNAL_RECEIVED_ACTION = "CamelSWFSignalReceivedAction";
    String GET_STATE_ACTION = "CamelSWFGetStateAction";
    String TASK_TOKEN = "CamelSWFTaskToken";
    String WORKFLOW_START_TIME = "CamelSWFWorkflowStartTime";
    String WORKFLOW_REPLAYING = "CamelSWFWorkflowReplaying";
}
