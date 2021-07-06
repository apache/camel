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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.kie.api.KieServices;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.task.model.Task;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.TaskAttachment;
import org.kie.server.api.model.instance.TaskInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.RuleServicesClient;
import org.kie.server.client.UserTaskServicesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JBPMProducer extends DefaultProducer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(JBPMProducer.class);

    private static KieCommands commandsFactory = KieServices.get().getCommands();

    private JBPMConfiguration configuration;
    private KieServicesClient kieServicesClient;
    private Map<JBPMOperation, JBPMOperationProcessor> operations;
    private Map<String, JBPMOperation> operationCachedToLowerOperation;

    public JBPMProducer(JBPMEndpoint endpoint, KieServicesClient kieServicesClient) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
        this.kieServicesClient = kieServicesClient;
        this.operations = new HashMap<>();
        this.operationCachedToLowerOperation = new HashMap<>();
        cacheJBPMOperationToLowerCase();
        initOperationProcessors();
        initOperationRules();
        initTaskOperation();
        initWorkItemOperation();
    }

    private void cacheJBPMOperationToLowerCase() {
        for (JBPMOperation operation : JBPMOperation.values()) {
            operationCachedToLowerOperation.put(operation.toString().toLowerCase(), operation);
        }
    }

    public void process(Exchange exchange) throws Exception {
        getOperation(exchange).execute(kieServicesClient, configuration, exchange);
    }

    JBPMOperationProcessor getOperation(Exchange exchange) {
        String operation = exchange.getIn().getHeader(JBPMConstants.OPERATION, String.class);
        if (operation == null && configuration.getOperation() != null) {
            operation = JBPMConstants.OPERATION + configuration.getOperation();
        }
        if (operation == null) {
            operation = JBPMConstants.OPERATION + JBPMOperation.startProcess;
        }
        LOGGER.trace("Operation: [{}]", operation);
        return operations.get(operationCachedToLowerOperation.get(operation.toLowerCase()));
    }

    private abstract class JBPMOperationProcessor {

        List<String> getStatuses(JBPMConfiguration configuration, Exchange exchange) {
            List<String> statusList = exchange.getIn().getHeader(JBPMConstants.STATUS_LIST, List.class);
            if (statusList == null) {
                statusList = configuration.getStatuses();
            }
            return statusList;
        }

        List<String> getEntities(JBPMConfiguration configuration, Exchange exchange) {
            List<String> entityList = exchange.getIn().getHeader(JBPMConstants.ENTITY_LIST, List.class);
            if (entityList == null) {
                entityList = configuration.getEntities();
            }
            return entityList;
        }

        Long getAttachmentId(JBPMConfiguration configuration, Exchange exchange) {
            Long attachmentId = exchange.getIn().getHeader(JBPMConstants.ATTACHMENT_ID, Long.class);
            if (attachmentId == null) {
                attachmentId = configuration.getAttachmentId();
            }
            return attachmentId;
        }

        Long getContentId(JBPMConfiguration configuration, Exchange exchange) {
            Long contentId = exchange.getIn().getHeader(JBPMConstants.CONTENT_ID, Long.class);
            if (contentId == null) {
                contentId = configuration.getContentId();
            }
            return contentId;
        }

        String getTargetUserId(JBPMConfiguration configuration, Exchange exchange) {
            String userId = exchange.getIn().getHeader(JBPMConstants.TARGET_USER_ID, String.class);
            if (userId == null) {
                userId = configuration.getTargetUserId();
            }
            return userId;
        }

        Integer getPage(JBPMConfiguration configuration, Exchange exchange) {
            Integer page = exchange.getIn().getHeader(JBPMConstants.RESULT_PAGE, Integer.class);
            if (page == null) {
                page = configuration.getPage();
            }
            return page;
        }

        Integer getPageSize(JBPMConfiguration configuration, Exchange exchange) {
            Integer pageSize = exchange.getIn().getHeader(JBPMConstants.RESULT_PAGE_SIZE, Integer.class);
            if (pageSize == null) {
                pageSize = configuration.getPageSize();
            }
            return pageSize;
        }

        Task getTask(JBPMConfiguration configuration, Exchange exchange) {
            Task task = exchange.getIn().getHeader(JBPMConstants.TASK, Task.class);
            if (task == null) {
                task = configuration.getTask();
            }
            return task;
        }

        String getUserId(JBPMConfiguration configuration, Exchange exchange) {
            String userId = exchange.getIn().getHeader(JBPMConstants.USER_ID, String.class);
            if (userId == null) {
                userId = configuration.getUserId();
            }
            return userId;
        }

        Long getTaskId(JBPMConfiguration configuration, Exchange exchange) {
            Long taskId = exchange.getIn().getHeader(JBPMConstants.TASK_ID, Long.class);
            if (taskId == null) {
                taskId = configuration.getTaskId();
            }
            return taskId;
        }

        Long getWorkItemId(JBPMConfiguration configuration, Exchange exchange) {
            Long workItemId = exchange.getIn().getHeader(JBPMConstants.WORK_ITEM_ID, Long.class);
            if (workItemId == null) {
                workItemId = configuration.getWorkItemId();
            }
            return workItemId;
        }

        String getIdentifier(JBPMConfiguration configuration, Exchange exchange) {
            String identifier = exchange.getIn().getHeader(JBPMConstants.IDENTIFIER, String.class);
            if (identifier == null) {
                identifier = configuration.getIdentifier();
            }
            return identifier;
        }

        Integer getMaxNumber(JBPMConfiguration configuration, Exchange exchange) {
            Integer max = exchange.getIn().getHeader(JBPMConstants.MAX_NUMBER, Integer.class);
            if (max == null) {
                max = configuration.getMaxNumber();
            }
            return max;
        }

        Object getEvent(JBPMConfiguration configuration, Exchange exchange) {
            Object event = exchange.getIn().getHeader(JBPMConstants.EVENT);
            if (event == null) {
                event = configuration.getEvent();
            }
            return event;
        }

        String getEventType(JBPMConfiguration configuration, Exchange exchange) {
            String eventType = exchange.getIn().getHeader(JBPMConstants.EVENT_TYPE, String.class);
            if (eventType == null) {
                eventType = configuration.getEventType();
            }
            return eventType;
        }

        String getProcessId(JBPMConfiguration configuration, Exchange exchange) {
            String processId = exchange.getIn().getHeader(JBPMConstants.PROCESS_ID, String.class);
            if (processId == null) {
                processId = configuration.getProcessId();
            }
            return processId;
        }

        Long getProcessInstanceId(JBPMConfiguration configuration, Exchange exchange) {
            Long processInstanceId = exchange.getIn().getHeader(JBPMConstants.PROCESS_INSTANCE_ID, Long.class);
            if (processInstanceId == null) {
                processInstanceId = configuration.getProcessInstanceId();
            }
            return processInstanceId;
        }

        Map<String, Object> getParameters(JBPMConfiguration configuration, Exchange exchange) {
            Map<String, Object> parameters = exchange.getIn().getHeader(JBPMConstants.PARAMETERS, Map.class);
            if (parameters == null) {
                parameters = configuration.getParameters();
            }
            return parameters;
        }

        Object getValue(JBPMConfiguration configuration, Exchange exchange) {
            Object value = exchange.getIn().getHeader(JBPMConstants.VALUE);
            if (value == null) {
                value = configuration.getValue();
            }
            return value;
        }

        Message getResultMessage(Exchange exchange) {
            return ExchangeHelper.isOutCapable(exchange) ? exchange.getOut() : exchange.getIn();
        }

        long safe(Long aLong) {
            return aLong != null ? aLong : 0;
        }

        void setResult(Exchange exchange, Object result) {
            getResultMessage(exchange).setBody(result);
        }

        abstract void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange);
    }

    private void initOperationProcessors() {
        // PROCESS OPERATIONS
        operations.put(JBPMOperation.startProcess, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
                Long processInstance = processClient.startProcess(configuration.getDeploymentId(),
                        getProcessId(configuration, exchange), getParameters(configuration, exchange));
                setResult(exchange, processInstance);
            }
        });
        operations.put(JBPMOperation.abortProcessInstance, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
                processClient.abortProcessInstance(configuration.getDeploymentId(),
                        safe(getProcessInstanceId(configuration, exchange)));
            }
        });
        operations.put(JBPMOperation.signalEvent, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
                Long processInstanceId = getProcessInstanceId(configuration, exchange);
                if (processInstanceId != null) {
                    processClient.signalProcessInstance(configuration.getDeploymentId(), processInstanceId,
                            getEventType(configuration, exchange),
                            getEvent(configuration, exchange));
                } else {
                    processClient.signal(configuration.getDeploymentId(), getEventType(configuration, exchange),
                            getEvent(configuration, exchange));
                }
            }
        });
        operations.put(JBPMOperation.getProcessInstance, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
                ProcessInstance processInstance = processClient.getProcessInstance(configuration.getDeploymentId(),
                        safe(getProcessInstanceId(configuration, exchange)));
                setResult(exchange, processInstance);
            }
        });

        operations.put(JBPMOperation.getProcessInstances, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
                Collection<ProcessInstance> processInstances = queryClient
                        .findProcessInstances(getPage(configuration, exchange), getPageSize(configuration, exchange));
                setResult(exchange, processInstances);
            }
        });
    }

    private void initOperationRules() {
        // RULE OPERATIONS
        operations.put(JBPMOperation.fireAllRules, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                RuleServicesClient ruleClient = kieServicesClient.getServicesClient(RuleServicesClient.class);
                List<Command<?>> commands = new ArrayList<Command<?>>();
                BatchExecutionCommand executionCommand = commandsFactory.newBatchExecution(commands);

                Integer max = getMaxNumber(configuration, exchange);
                if (max != null) {
                    commands.add(commandsFactory.newFireAllRules(max));
                } else {
                    commands.add(commandsFactory.newFireAllRules());
                }
                ServiceResponse<ExecutionResults> reply
                        = ruleClient.executeCommandsWithResults(configuration.getDeploymentId(), executionCommand);
                setResult(exchange, reply.getResult());
            }
        });
        operations.put(JBPMOperation.getGlobal, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                RuleServicesClient ruleClient = kieServicesClient.getServicesClient(RuleServicesClient.class);
                List<Command<?>> commands = new ArrayList<Command<?>>();
                BatchExecutionCommand executionCommand = commandsFactory.newBatchExecution(commands);
                String identifier = getIdentifier(configuration, exchange);
                commands.add(commandsFactory.newGetGlobal(identifier, identifier));

                ServiceResponse<ExecutionResults> reply
                        = ruleClient.executeCommandsWithResults(configuration.getDeploymentId(), executionCommand);
                setResult(exchange, reply.getResult().getValue(identifier));
            }
        });
        operations.put(JBPMOperation.setGlobal, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                RuleServicesClient ruleClient = kieServicesClient.getServicesClient(RuleServicesClient.class);
                List<Command<?>> commands = new ArrayList<Command<?>>();
                BatchExecutionCommand executionCommand = commandsFactory.newBatchExecution(commands);

                commands.add(commandsFactory.newSetGlobal(getIdentifier(configuration, exchange),
                        getValue(configuration, exchange)));

                ruleClient.executeCommandsWithResults(configuration.getDeploymentId(), executionCommand);
            }
        });
    }

    private void initWorkItemOperation() {
        // WORK ITEM OPERATIONS
        operations.put(JBPMOperation.abortWorkItem, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
                processClient.abortWorkItem(configuration.getDeploymentId(),
                        safe(getProcessInstanceId(configuration, exchange)), safe(getWorkItemId(configuration, exchange)));
            }
        });
        operations.put(JBPMOperation.completeWorkItem, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
                processClient.completeWorkItem(configuration.getDeploymentId(),
                        safe(getProcessInstanceId(configuration, exchange)), safe(getWorkItemId(configuration, exchange)),
                        getParameters(configuration, exchange));
            }
        });
    }

    private void initTaskOperation() {
        // TASK OPERATIONS
        operations.put(JBPMOperation.activateTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.activateTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.claimTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.claimTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.completeTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.completeAutoProgress(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange),
                        getParameters(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.delegateTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.delegateTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange),
                        getTargetUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.exitTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.exitTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.failTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.failTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange),
                        getParameters(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.getAttachment, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                TaskAttachment attachment = taskClient.getTaskAttachmentById(configuration.getDeploymentId(),
                        safe(getTaskId(configuration, exchange)),
                        safe(getAttachmentId(configuration, exchange)));
                setResult(exchange, attachment);
            }
        });
        operations.put(JBPMOperation.getTasksAssignedAsBusinessAdministrator, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                List<TaskSummary> taskSummaries = taskClient.findTasksAssignedAsBusinessAdministrator(
                        getUserId(configuration, exchange), getPage(configuration, exchange),
                        getPageSize(configuration, exchange));
                setResult(exchange, taskSummaries);
            }
        });
        operations.put(JBPMOperation.getTasksAssignedAsPotentialOwnerByStatus, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                List<TaskSummary> taskSummaries = taskClient.findTasksAssignedAsPotentialOwner(
                        getUserId(configuration, exchange), getStatuses(configuration, exchange),
                        getPage(configuration, exchange), getPageSize(configuration, exchange));
                setResult(exchange, taskSummaries);
            }
        });
        operations.put(JBPMOperation.getTaskByWorkItem, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                TaskInstance task = taskClient.findTaskByWorkItemId(safe(getWorkItemId(configuration, exchange)));
                setResult(exchange, task);
            }
        });
        operations.put(JBPMOperation.getTaskBy, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                TaskInstance task = taskClient.findTaskById(safe(getTaskId(configuration, exchange)));
                setResult(exchange, task);
            }
        });
        operations.put(JBPMOperation.getTaskContent, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                Map<String, Object> content = taskClient.getTaskOutputContentByTaskId(configuration.getDeploymentId(),
                        safe(getTaskId(configuration, exchange)));
                setResult(exchange, content);
            }
        });
        operations.put(JBPMOperation.getTasksByProcessInstance, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                List<TaskSummary> processInstanceIds = taskClient.findTasksByStatusByProcessInstanceId(
                        safe(getProcessInstanceId(configuration, exchange)), Collections.emptyList(),
                        getPage(configuration, exchange), getPageSize(configuration, exchange));
                setResult(exchange, processInstanceIds);
            }
        });
        operations.put(JBPMOperation.getTasksByStatusByProcessInstance, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                List<TaskSummary> taskSummaryList
                        = taskClient.findTasksByStatusByProcessInstanceId(safe(getProcessInstanceId(configuration, exchange)),
                                getStatuses(configuration, exchange), getPage(configuration, exchange),
                                getPageSize(configuration, exchange));
                setResult(exchange, taskSummaryList);
            }
        });
        operations.put(JBPMOperation.getTasksOwned, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                List<TaskSummary> summaryList
                        = taskClient.findTasksOwned(getUserId(configuration, exchange), getPage(configuration, exchange),
                                getPageSize(configuration, exchange));
                setResult(exchange, summaryList);
            }
        });
        operations.put(JBPMOperation.nominateTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.nominateTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange),
                        getEntities(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.releaseTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.releaseTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.resumeTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.resumeTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.skipTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.skipTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.startTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.startTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.stopTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.stopTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });
        operations.put(JBPMOperation.suspendTask, new JBPMOperationProcessor() {
            @Override
            void execute(KieServicesClient kieServicesClient, JBPMConfiguration configuration, Exchange exchange) {
                UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
                taskClient.suspendTask(configuration.getDeploymentId(), safe(getTaskId(configuration, exchange)),
                        getUserId(configuration, exchange));
            }
        });

    }

}
