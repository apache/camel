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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerMetaData;
import org.quartz.impl.matchers.GroupMatcher;

@DevConsole(name = "quartz", description = "Quartz Scheduler")
public class QuartzConsole extends AbstractDevConsole {

    public record JobEntry(
            String jobId, String triggerType, String cron, String routeId, String uri, Long prevFireTime,
            Long fireTime, Long nextFireTime, Long finalFireTime, boolean recovering, int refireCount,
            int misfireInstruction) {
    }

    public record TriggerEntry(String routeId, String triggerType, String cron, String repeatInterval) {
    }

    public record Response(
            String schedulerName, String schedulerInstanceId, String quartzVersion, Long runningSince,
            Integer totalCounter, Boolean started, Boolean shutdown, Boolean inStandbyMode, String threadPoolClass,
            Integer threadPoolSize,
            @Metadata(description = "The job store class name (field kept as jpbStoreClass for backwards compatibility)") String jpbStoreClass,
            Boolean jpbStoreClustered, Boolean jpbStoreSupportsPersistence, Integer currentExecutingJobs,
            @Metadata(description = "Only present when there are any currently executing jobs") List<JobEntry> jobs,
            @Metadata(description = "Only present when there are any scheduled triggers") List<TriggerEntry> triggers) {
    }

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
                sb.append(String.format("    Scheduler Name: %s%n", scheduler.getSchedulerName()));
                sb.append(String.format("    Scheduler Id: %s%n", scheduler.getSchedulerInstanceId()));
                SchedulerMetaData meta = scheduler.getMetaData();
                if (meta != null) {
                    sb.append(String.format("    Quartz Version: %s%n", meta.getVersion()));
                    String since = SimpleDateFormat.getDateTimeInstance().format(meta.getRunningSince());
                    sb.append(String.format("    Running Since: %s%n", since));
                    sb.append(String.format("    Total Counter: %s%n", meta.getNumberOfJobsExecuted()));
                    sb.append(String.format("    Started: %s%n", meta.isStarted()));
                    sb.append(String.format("    Shutdown: %s%n", meta.isShutdown()));
                    sb.append(String.format("    In Standby Mode: %s%n", meta.isInStandbyMode()));
                    sb.append(String.format("    Thread Pool Class: %s%n", meta.getThreadPoolClass().getName()));
                    sb.append(String.format("    Thread Pool Size: %d%n", meta.getThreadPoolSize()));
                    sb.append(String.format("    Job Store Class: %s%n", meta.getJobStoreClass().getName()));
                    sb.append(String.format("    Job Store Clustered: %s%n", meta.isJobStoreClustered()));
                    sb.append(String.format("    Job Store Supports Persistence: %s%n", meta.isJobStoreSupportsPersistence()));
                }

                List<JobExecutionContext> jobs = scheduler.getCurrentlyExecutingJobs();
                sb.append(String.format("    Currently Executing Jobs: %d%n", jobs.size()));
                if (!jobs.isEmpty()) {
                    sb.append("\n");
                    sb.append("Jobs:\n");
                    sb.append("\n");
                    for (JobExecutionContext job : jobs) {
                        sb.append(String.format("        Job Id: %s%n", job.getFireInstanceId()));

                        String type = (String) job.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE);
                        sb.append(String.format("        Trigger Type: %s%n", type));
                        String cron = (String) job.getJobDetail().getJobDataMap()
                                .get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION);
                        if (cron != null) {
                            sb.append(String.format("        Cron: %s%n", cron));
                        }
                        String routeId = (String) job.getJobDetail().getJobDataMap().get("routeId");
                        if (routeId != null) {
                            sb.append(String.format("        Route Id: %s%n", routeId));
                        }
                        String uri = (String) job.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_ENDPOINT_URI);
                        if (cron != null) {
                            sb.append(String.format("        Endpoint Uri: %s%n", uri));
                        }
                        Date d = job.getTrigger().getPreviousFireTime();
                        if (d != null) {
                            sb.append(String.format("        Prev Fire Time: %s%n",
                                    SimpleDateFormat.getDateTimeInstance().format(d)));
                        }
                        d = job.getFireTime();
                        if (d != null) {
                            sb.append(
                                    String.format("        Fire Time: %s%n", SimpleDateFormat.getDateTimeInstance().format(d)));
                        }
                        d = job.getTrigger().getNextFireTime();
                        if (d != null) {
                            sb.append(String.format("        Next Fire Time: %s%n",
                                    SimpleDateFormat.getDateTimeInstance().format(d)));
                        }
                        d = job.getTrigger().getFinalFireTime();
                        if (d != null) {
                            sb.append(String.format("        Final Fire Time: %s%n",
                                    SimpleDateFormat.getDateTimeInstance().format(d)));
                        }
                        sb.append(String.format("        Recovering: %s%n", job.isRecovering()));
                        sb.append(String.format("        Refire Count: %s%n", job.getRefireCount()));
                        sb.append(String.format("        Misfire Instruction: %s%n", job.getTrigger().getMisfireInstruction()));

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
        String schedulerName = null;
        String schedulerInstanceId = null;
        String quartzVersion = null;
        Long runningSince = null;
        Integer totalCounter = null;
        Boolean started = null;
        Boolean shutdown = null;
        Boolean inStandbyMode = null;
        String threadPoolClass = null;
        Integer threadPoolSize = null;
        String jpbStoreClass = null;
        Boolean jpbStoreClustered = null;
        Boolean jpbStoreSupportsPersistence = null;
        Integer currentExecutingJobs = null;
        List<JobEntry> jobs = null;
        List<TriggerEntry> triggers = null;

        QuartzComponent quartz = getCamelContext().getComponent("quartz", QuartzComponent.class);
        if (quartz != null) {
            Scheduler scheduler = quartz.getScheduler();
            try {
                schedulerName = scheduler.getSchedulerName();
                schedulerInstanceId = scheduler.getSchedulerInstanceId();
                SchedulerMetaData meta = scheduler.getMetaData();
                if (meta != null) {
                    quartzVersion = meta.getVersion();
                    runningSince = meta.getRunningSince().getTime();
                    totalCounter = meta.getNumberOfJobsExecuted();
                    started = meta.isStarted();
                    shutdown = meta.isShutdown();
                    inStandbyMode = meta.isInStandbyMode();
                    threadPoolClass = meta.getThreadPoolClass().getName();
                    threadPoolSize = meta.getThreadPoolSize();
                    jpbStoreClass = meta.getJobStoreClass().getName();
                    jpbStoreClustered = meta.isJobStoreClustered();
                    jpbStoreSupportsPersistence = meta.isJobStoreSupportsPersistence();
                }

                List<JobExecutionContext> executingJobs = scheduler.getCurrentlyExecutingJobs();
                currentExecutingJobs = executingJobs.size();
                if (!executingJobs.isEmpty()) {
                    List<JobEntry> arr = new ArrayList<>();
                    for (JobExecutionContext job : executingJobs) {
                        String type = (String) job.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE);
                        String cron = (String) job.getJobDetail().getJobDataMap()
                                .get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION);
                        String routeId = (String) job.getJobDetail().getJobDataMap().get("routeId");
                        String uri = null;
                        if (cron != null) {
                            uri = (String) job.getJobDetail().getJobDataMap().get(QuartzConstants.QUARTZ_ENDPOINT_URI);
                        }
                        Date prevFireTimeD = job.getTrigger().getPreviousFireTime();
                        Date fireTimeD = job.getFireTime();
                        Date nextFireTimeD = job.getTrigger().getNextFireTime();
                        Date finalFireTimeD = job.getTrigger().getFinalFireTime();

                        arr.add(new JobEntry(
                                job.getFireInstanceId(), type, cron, routeId, uri,
                                prevFireTimeD != null ? prevFireTimeD.getTime() : null,
                                fireTimeD != null ? fireTimeD.getTime() : null,
                                nextFireTimeD != null ? nextFireTimeD.getTime() : null,
                                finalFireTimeD != null ? finalFireTimeD.getTime() : null, job.isRecovering(),
                                job.getRefireCount(), job.getTrigger().getMisfireInstruction()));
                    }
                    jobs = arr;
                }

                // all scheduled triggers (for TUI consumer schedule display)
                Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup());
                if (!jobKeys.isEmpty()) {
                    List<TriggerEntry> arr = new ArrayList<>();
                    for (JobKey jobKey : jobKeys) {
                        JobDetail job = scheduler.getJobDetail(jobKey);
                        if (job != null) {
                            String routeId = (String) job.getJobDataMap().get("routeId");
                            String type = (String) job.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE);
                            String cron = (String) job.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION);
                            String interval
                                    = (String) job.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_INTERVAL);
                            arr.add(new TriggerEntry(routeId, type, cron, interval));
                        }
                    }
                    triggers = arr;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        Response response = new Response(
                schedulerName, schedulerInstanceId, quartzVersion, runningSince, totalCounter, started, shutdown,
                inStandbyMode, threadPoolClass, threadPoolSize, jpbStoreClass, jpbStoreClustered,
                jpbStoreSupportsPersistence, currentExecutingJobs, jobs, triggers);
        return JsonRecordSupport.toJsonObject(response);
    }
}
