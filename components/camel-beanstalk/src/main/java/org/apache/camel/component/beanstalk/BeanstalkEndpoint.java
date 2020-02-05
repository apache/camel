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
package org.apache.camel.component.beanstalk;

import com.surftools.BeanstalkClient.Client;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.beanstalk.processors.BuryCommand;
import org.apache.camel.component.beanstalk.processors.Command;
import org.apache.camel.component.beanstalk.processors.DeleteCommand;
import org.apache.camel.component.beanstalk.processors.KickCommand;
import org.apache.camel.component.beanstalk.processors.PutCommand;
import org.apache.camel.component.beanstalk.processors.ReleaseCommand;
import org.apache.camel.component.beanstalk.processors.TouchCommand;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * The beanstalk component is used for job retrieval and post-processing of Beanstalk jobs.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "beanstalk", title = "Beanstalk", syntax = "beanstalk:connectionSettings", label = "messaging")
public class BeanstalkEndpoint extends ScheduledPollEndpoint implements AsyncEndpoint {
    final ConnectionSettings conn;

    @UriPath(description = "Connection settings host:port/tube")
    private String connectionSettings;
    @UriParam
    private BeanstalkCommand command = BeanstalkCommand.put;
    @UriParam(defaultValue = "" + BeanstalkComponent.DEFAULT_PRIORITY)
    private long jobPriority = BeanstalkComponent.DEFAULT_PRIORITY;
    @UriParam(defaultValue = "" + BeanstalkComponent.DEFAULT_DELAY)
    private int jobDelay = BeanstalkComponent.DEFAULT_DELAY;
    @UriParam(defaultValue = "" + BeanstalkComponent.DEFAULT_TIME_TO_RUN)
    private int jobTimeToRun = BeanstalkComponent.DEFAULT_TIME_TO_RUN;
    @UriParam(label = "consumer")
    private BeanstalkCommand onFailure = BeanstalkCommand.bury;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean useBlockIO = true;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean awaitJob = true;

    public BeanstalkEndpoint(final String uri, final Component component, final ConnectionSettings conn, final String connectionSettings) {
        super(uri, component);
        this.conn = conn;
        this.connectionSettings = connectionSettings;
    }

    public String getConnectionSettings() {
        return connectionSettings;
    }

    public ConnectionSettings getConnection() {
        return conn;
    }

    public ConnectionSettings getConn() {
        return conn;
    }

    public BeanstalkCommand getCommand() {
        return command;
    }

    /**
     * put means to put the job into Beanstalk. Job body is specified in the Camel message body. Job ID will be returned in beanstalk.jobId message header.
     * delete, release, touch or bury expect Job ID in the message header beanstalk.jobId. Result of the operation is returned in beanstalk.result message header
     * kick expects the number of jobs to kick in the message body and returns the number of jobs actually kicked out in the message header beanstalk.result.
     */
    public void setCommand(BeanstalkCommand command) {
        this.command = command;
    }

    public long getJobPriority() {
        return jobPriority;
    }

    /**
     * Job priority. (0 is the highest, see Beanstalk protocol)
     */
    public void setJobPriority(long jobPriority) {
        this.jobPriority = jobPriority;
    }

    public int getJobDelay() {
        return jobDelay;
    }

    /**
     * Job delay in seconds.
     */
    public void setJobDelay(int jobDelay) {
        this.jobDelay = jobDelay;
    }

    public int getJobTimeToRun() {
        return jobTimeToRun;
    }

    /**
     * Job time to run in seconds. (when 0, the beanstalkd daemon raises it to 1 automatically, see Beanstalk protocol)
     */
    public void setJobTimeToRun(int jobTimeToRun) {
        this.jobTimeToRun = jobTimeToRun;
    }

    public BeanstalkCommand getOnFailure() {
        return onFailure;
    }

    /**
     * Command to use when processing failed.
     */
    public void setOnFailure(BeanstalkCommand onFailure) {
        this.onFailure = onFailure;
    }

    public boolean isUseBlockIO() {
        return useBlockIO;
    }

    /**
     * Whether to use blockIO.
     */
    public void setUseBlockIO(boolean useBlockIO) {
        this.useBlockIO = useBlockIO;
    }

    public boolean isAwaitJob() {
        return awaitJob;
    }

    /**
     * Whether to wait for job to complete before ack the job from beanstalk
     */
    public void setAwaitJob(boolean awaitJob) {
        this.awaitJob = awaitJob;
    }

    /**
     * Creates Camel producer.
     * <p/>
     * Depending on the command parameter (see {@link BeanstalkComponent} URI) it
     * will create one of the producer implementations.
     *
     * @return {@link Producer} instance
     * @throws IllegalArgumentException when {@link ConnectionSettings} cannot
     *                                  create a writable {@link Client}
     */
    @Override
    public Producer createProducer() throws Exception {
        Command cmd;
        if (BeanstalkComponent.COMMAND_PUT.equals(command.name())) {
            cmd = new PutCommand(this);
        } else if (BeanstalkComponent.COMMAND_RELEASE.equals(command.name())) {
            cmd = new ReleaseCommand(this);
        } else if (BeanstalkComponent.COMMAND_BURY.equals(command.name())) {
            cmd = new BuryCommand(this);
        } else if (BeanstalkComponent.COMMAND_TOUCH.equals(command.name())) {
            cmd = new TouchCommand(this);
        } else if (BeanstalkComponent.COMMAND_DELETE.equals(command.name())) {
            cmd = new DeleteCommand(this);
        } else if (BeanstalkComponent.COMMAND_KICK.equals(command.name())) {
            cmd = new KickCommand(this);
        } else {
            throw new IllegalArgumentException(String.format("Unknown command for Beanstalk endpoint: %s", command));
        }

        return new BeanstalkProducer(this, cmd);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        BeanstalkConsumer consumer = new BeanstalkConsumer(this, processor);
        consumer.setAwaitJob(isAwaitJob());
        consumer.setOnFailure(getOnFailure());
        consumer.setUseBlockIO(isUseBlockIO());
        configureConsumer(consumer);
        return consumer;
    }
}
