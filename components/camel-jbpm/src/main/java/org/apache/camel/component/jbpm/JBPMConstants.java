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

public interface JBPMConstants {
    String VALUE = "CamelJBPMValue";
    String OPERATION = "CamelJBPMOperation";
    String PROCESS_ID = "CamelJBPMProcessId";
    String PROCESS_INSTANCE_ID = "CamelJBPMProcessInstanceId";
    String PARAMETERS = "CamelJBPMParameters";
    String EVENT_TYPE = "CamelJBPMEventType";
    String EVENT = "CamelJBPMEvent";
    String MAX_NUMBER = "CamelJBPMMaxNumber";
    String IDENTIFIER = "CamelJBPMIdentifier";
    String WORK_ITEM_ID = "CamelJBPMWorkItemId";
    String TASK_ID = "CamelJBPMTaskId";
    String TASK = "CamelJBPMTask";
    String USER_ID = "CamelJBPMUserId";
    String TARGET_USER_ID = "CamelJBPMTargetUserId";    
    String ATTACHMENT_ID = "CamelJBPMAttachmentId";
    String CONTENT_ID = "CamelJBPMContentId";
    String ENTITY_LIST = "CamelJBPMEntityList";
    String STATUS_LIST = "CamelJBPMStatusList";
    String RESULT_PAGE = "CamelJBPMResultPage";
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
