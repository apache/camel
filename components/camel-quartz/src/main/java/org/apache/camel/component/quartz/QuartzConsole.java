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
package org.apache.camel.component.quartz;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerMetaData;

@DevConsole("quartz")
public class QuartzConsole extends AbstractDevConsole {

    public QuartzConsole() {
        super("camel", "quartz", "Quartz", "Quartz Scheduler");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        QuartzComponent quartz = getCamelContext().getComponent("quartz", QuartzComponent.class);
        if (quartz != null) {
            Scheduler scheduler = quartz.getScheduler();
            try {
                sb.append(String.format("    Scheduler Name: %s\n", scheduler.getSchedulerName()));
                sb.append(String.format("    Scheduler Id: %s\n", scheduler.getSchedulerInstanceId()));
                SchedulerMetaData meta = scheduler.getMetaData();
                if (meta != null) {
                    sb.append(String.format("    Quartz Version: %s\n", meta.getVersion()));
                    String since = SimpleDateFormat.getDateTimeInstance().format(meta.getRunningSince());
                    sb.append(String.format("    Running Since: %s\n", since));
                    sb.append(String.format("    Total Counter: %s\n", meta.getNumberOfJobsExecuted()));
                    sb.append(String.format("    Started: %s\n", meta.isStarted()));
                    sb.append(String.format("    Shutdown: %s\n", meta.isShutdown()));
                    sb.append(String.format("    In Standby Mode: %s\n", meta.isInStandbyMode()));
                    sb.append(String.format("    Thread Pool Class: %s\n", meta.getThreadPoolClass().getName()));
                    sb.append(String.format("    Thread Pool Size: %d\n", meta.getThreadPoolSize()));
                    sb.append(String.format("    Job Store Class: %s\n", meta.getJobStoreClass().getName()));
                    sb.append(String.format("    Job Store Clustered: %s\n", meta.isJobStoreClustered()));
                    sb.append(String.format("    Job Store Supports Persistence: %s\n", meta.isJobStoreSupportsPersistence()));
                }

                List<JobExecutionContext> jobs = scheduler.getCurrentlyExecutingJobs();
                sb.append(String.format("    Currently Executing Jobs: %d\n", jobs.size()));
                if (!jobs.isEmpty()) {
                    sb.append("\n");
                    sb.append("Jobs:\n");
                    sb.append("\n");
                    for (JobExecutionContext job : jobs) {
                        sb.append(String.format("        Job Id: %s\n", job.getFireInstanceId()));

                        String type = (String) job.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE);
                        sb.append(String.format("        Trigger Type: %s\n", type));
                        String cron = (String) job.getJobDetail().getJobDataMap()
                                .get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION);
                        if (cron != null) {
                            sb.append(String.format("        Cron: %s\n", cron));
                        }
                        String routeId = (String) job.getJobDetail().getJobDataMap().get("routeId");
                        if (routeId != null) {
                            sb.append(String.format("        Route Id: %s\n", routeId));
                        }
                        String uri = (String) job.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_ENDPOINT_URI);
                        if (cron != null) {
                            sb.append(String.format("        Endpoint Uri: %s\n", uri));
                        }
                        Date d = job.getTrigger().getPreviousFireTime();
                        if (d != null) {
                            sb.append(String.format("        Prev Fire Time: %s\n",
                                    SimpleDateFormat.getDateTimeInstance().format(d)));
                        }
                        d = job.getFireTime();
                        if (d != null) {
                            sb.append(
                                    String.format("        Fire Time: %s\n", SimpleDateFormat.getDateTimeInstance().format(d)));
                        }
                        d = job.getTrigger().getNextFireTime();
                        if (d != null) {
                            sb.append(String.format("        Next Fire Time: %s\n",
                                    SimpleDateFormat.getDateTimeInstance().format(d)));
                        }
                        d = job.getTrigger().getFinalFireTime();
                        if (d != null) {
                            sb.append(String.format("        Final Fire Time: %s\n",
                                    SimpleDateFormat.getDateTimeInstance().format(d)));
                        }
                        sb.append(String.format("        Recovering: %s\n", job.isRecovering()));
                        sb.append(String.format("        Refire Count: %s\n", job.getRefireCount()));
                        sb.append(String.format("        Misfire Instruction: %s\n", job.getTrigger().getMisfireInstruction()));

                        sb.append("\n");
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        QuartzComponent quartz = getCamelContext().getComponent("quartz", QuartzComponent.class);
        if (quartz != null) {
            Scheduler scheduler = quartz.getScheduler();
            try {
                root.put("schedulerName", scheduler.getSchedulerName());
                root.put("schedulerInstanceId", scheduler.getSchedulerInstanceId());
                SchedulerMetaData meta = scheduler.getMetaData();
                if (meta != null) {
                    root.put("quartzVersion", meta.getVersion());
                    root.put("runningSince", meta.getRunningSince().getTime());
                    root.put("totalCounter", meta.getNumberOfJobsExecuted());
                    root.put("started", meta.isStarted());
                    root.put("shutdown", meta.isShutdown());
                    root.put("inStandbyMode", meta.isInStandbyMode());
                    root.put("threadPoolClass", meta.getThreadPoolClass().getName());
                    root.put("threadPoolSize", meta.getThreadPoolSize());
                    root.put("jpbStoreClass", meta.getJobStoreClass().getName());
                    root.put("jpbStoreClustered", meta.isJobStoreClustered());
                    root.put("jpbStoreSupportsPersistence", meta.isJobStoreSupportsPersistence());
                }

                List<JobExecutionContext> jobs = scheduler.getCurrentlyExecutingJobs();
                root.put("currentExecutingJobs", jobs.size());
                if (!jobs.isEmpty()) {
                    JsonArray arr = new JsonArray();
                    root.put("jobs", arr);
                    for (JobExecutionContext job : jobs) {
                        JsonObject jo = new JsonObject();
                        jo.put("jobId", job.getFireInstanceId());

                        String type = (String) job.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE);
                        jo.put("triggerType", type);
                        String cron = (String) job.getJobDetail().getJobDataMap()
                                .get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION);
                        if (cron != null) {
                            jo.put("cron", cron);
                        }
                        String routeId = (String) job.getJobDetail().getJobDataMap().get("routeId");
                        if (routeId != null) {
                            jo.put("routeId", routeId);
                        }
                        String uri = (String) job.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_ENDPOINT_URI);
                        if (cron != null) {
                            jo.put("uri", uri);
                        }
                        Date d = job.getTrigger().getPreviousFireTime();
                        if (d != null) {
                            jo.put("prevFireTime", d.getTime());
                        }
                        d = job.getFireTime();
                        if (d != null) {
                            jo.put("fireTime", d.getTime());
                        }
                        d = job.getTrigger().getNextFireTime();
                        if (d != null) {
                            jo.put("nextFireTime", d.getTime());
                        }
                        d = job.getTrigger().getFinalFireTime();
                        if (d != null) {
                            jo.put("finalFireTime", d.getTime());
                        }
                        jo.put("recovering", job.isRecovering());
                        jo.put("refireCount", job.getRefireCount());
                        jo.put("misfireInstruction", job.getTrigger().getMisfireInstruction());
                        arr.add(jo);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        return root;
    }
}
