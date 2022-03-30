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
package org.apache.camel.component.jbpm;

import org.apache.camel.spi.Metadata;

public interface JBPMConstants {
    @Metadata(label = "producer", description = "The value to assign to the global identifier", javaType = "Object")
    String VALUE = "CamelJBPMValue";
    @Metadata(label = "producer", description = "The operation to perform. The operation name must be prefixed with\n" +
                                                "CamelJBPMOperation and the name of the operation. See the full list\n" +
                                                "above. It is case-insensitive.",
              javaType = "String", defaultValue = "PUT")
    String OPERATION = "CamelJBPMOperation";
    @Metadata(label = "producer", description = "The id of the process that should be acted upon", javaType = "String")
    String PROCESS_ID = "CamelJBPMProcessId";
    @Metadata(label = "producer", description = "The id of the process instance", javaType = "Long")
    String PROCESS_INSTANCE_ID = "CamelJBPMProcessInstanceId";
    @Metadata(label = "producer", description = "The variables that should be set for various operations",
              javaType = "Map<String, Object>")
    String PARAMETERS = "CamelJBPMParameters";
    @Metadata(label = "producer", description = "The type of event to use when signalEvent operation is performed",
              javaType = "String")
    String EVENT_TYPE = "CamelJBPMEventType";
    @Metadata(label = "producer", description = "The type of the received event. Possible values defined here\n" +
                                                "org.infinispan.notifications.cachelistener.event.Event.Type",
              javaType = "Object")
    String EVENT = "CamelJBPMEvent";
    @Metadata(label = "producer", description = "The maximum number of rules that should be fired", javaType = "Integer")
    String MAX_NUMBER = "CamelJBPMMaxNumber";
    @Metadata(label = "producer", description = "The global identifier", javaType = "String")
    String IDENTIFIER = "CamelJBPMIdentifier";
    @Metadata(label = "producer", description = "The id of the work item", javaType = "Long")
    String WORK_ITEM_ID = "CamelJBPMWorkItemId";
    @Metadata(label = "producer", description = "The id of the task", javaType = "Long")
    String TASK_ID = "CamelJBPMTaskId";
    @Metadata(label = "producer", description = "The task instance to use with task operations",
              javaType = "org.kie.api.task.model.Task")
    String TASK = "CamelJBPMTask";
    @Metadata(label = "producer", description = "The userId to use with task operations", javaType = "String")
    String USER_ID = "CamelJBPMUserId";
    @Metadata(label = "producer", description = "The targetUserId used when delegating a task", javaType = "String")
    String TARGET_USER_ID = "CamelJBPMTargetUserId";
    @Metadata(label = "producer", description = "The attachId to use when retrieving attachments", javaType = "Long")
    String ATTACHMENT_ID = "CamelJBPMAttachmentId";
    @Metadata(label = "producer", description = "The contentId to use when retrieving attachments", javaType = "Long")
    String CONTENT_ID = "CamelJBPMContentId";
    @Metadata(label = "producer", description = "The potentialOwners when nominateTask operation is performed",
              javaType = "List<String>")
    String ENTITY_LIST = "CamelJBPMEntityList";
    @Metadata(label = "producer", description = "The list of status to use when filtering tasks.", javaType = "List<String>")
    String STATUS_LIST = "CamelJBPMStatusList";
    @Metadata(label = "producer", description = "The page to use when retrieving user tasks", javaType = "Integer")
    String RESULT_PAGE = "CamelJBPMResultPage";
    @Metadata(label = "producer", description = "The page size to use when retrieving user tasks", javaType = "Integer")
    String RESULT_PAGE_SIZE = "CamelJBPMResultPageSize";

    String JBPM_PROCESS_EVENT_LISTENER = "process";
    String JBPM_TASK_EVENT_LISTENER = "task";
    String JBPM_CASE_EVENT_LISTENER = "case";
    String JBPM_EVENT_EMITTER = "emitter";

    String GLOBAL_CAMEL_CONTEXT_SERVICE_KEY = "GlobalCamelService";
    String DEPLOYMENT_CAMEL_CONTEXT_SERVICE_KEY_POSTFIX = "_CamelService";
    String CAMEL_ENDPOINT_ID_WI_PARAM = "CamelEndpointId";
    String HANDLE_EXCEPTION_WI_PARAM = "HandleExceptions";
    String RESPONSE_WI_PARAM = "Response";
    String MESSAGE_WI_PARAM = "Message";

    String CAMEL_CONTEXT_BUILDER_KEY = "CamelContextBuilder";

}
