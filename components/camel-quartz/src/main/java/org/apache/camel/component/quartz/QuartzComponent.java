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
package org.apache.camel.component.quartz;

import java.net.URI;
import java.text.ParseException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

/**
 * A <a href="http://camel.apache.org/quartz.html">Quartz Component</a>
 * <p/>
 * For a bried tutorial on setting cron expression see
 * <a href="http://www.opensymphony.com/quartz/wikidocs/CronTriggers%20Tutorial.html">Quartz cron tutorial</a>.
 * 
 * @version $Revision:520964 $
 */
public class QuartzComponent extends DefaultComponent {
    private static final transient Log LOG = LogFactory.getLog(QuartzComponent.class);
    private SchedulerFactory factory;
    private Scheduler scheduler;
    private Map<Trigger, JobDetail> triggers;

    public QuartzComponent() {
    }

    public QuartzComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected QuartzEndpoint createEndpoint(final String uri, final String remaining, final Map parameters) throws Exception {
        QuartzEndpoint answer = new QuartzEndpoint(uri, this, getScheduler());

        // lets split the remaining into a group/name
        URI u = new URI(uri);
        String path = ObjectHelper.after(u.getPath(), "/");
        String host = u.getHost();
        String cron = getAndRemoveParameter(parameters, "cron", String.class);

        // group can be optional, if so set it to Camel
        String name;
        String group;
        if (ObjectHelper.isNotEmpty(path) && ObjectHelper.isNotEmpty(host)) {
            group = host;
            name = path;
        } else {
            group = "Camel";
            name = host;
        }

        // create the trigger either cron or simple
        Trigger trigger;
        if (ObjectHelper.isNotEmpty(cron)) {
            trigger = createCronTrigger(cron);
        } else {
            trigger = new SimpleTrigger();
        }
        answer.setTrigger(trigger);

        trigger.setName(name);
        trigger.setGroup(group);

        Map triggerParameters = IntrospectionSupport.extractProperties(parameters, "trigger.");
        Map jobParameters = IntrospectionSupport.extractProperties(parameters, "job.");

        setProperties(trigger, triggerParameters);
        setProperties(answer.getJobDetail(), jobParameters);

        return answer;
    }

    protected CronTrigger createCronTrigger(String path) throws ParseException {
        // replace _ back to space so its a cron expression
        String s = path.replaceAll("_", " ");
        // replace + back to space so its a cron expression
        s = s.replaceAll("\\+", " ");
        CronTrigger cron = new CronTrigger();
        cron.setCronExpression(s);
        return cron;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (scheduler == null) {
            scheduler = getScheduler();
        }
        LOG.debug("Starting Quartz scheduler: " + scheduler.getSchedulerName());
        scheduler.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (scheduler != null) {
            LOG.debug("Shutting down Quartz scheduler: " + scheduler.getSchedulerName());
            scheduler.shutdown();
        }
        super.doStop();
    }

    // Properties
    // -------------------------------------------------------------------------
    public SchedulerFactory getFactory() {
        if (factory == null) {
            factory = createSchedulerFactory();
        }
        return factory;
    }

    public void setFactory(final SchedulerFactory factory) {
        this.factory = factory;
    }

    public Scheduler getScheduler() throws SchedulerException {
        if (scheduler == null) {
            scheduler = createScheduler();
        }
        return scheduler;
    }

    public void setScheduler(final Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Map getTriggers() {
        return triggers;
    }

    public void setTriggers(final Map triggers) {
        this.triggers = triggers;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected SchedulerFactory createSchedulerFactory() {
        return new StdSchedulerFactory();
    }

    protected Scheduler createScheduler() throws SchedulerException {
        Scheduler scheduler = getFactory().getScheduler();
        scheduler.getContext().put(QuartzConstants.QUARTZ_CAMEL_CONTEXT, getCamelContext());
        return scheduler;
    }
}
