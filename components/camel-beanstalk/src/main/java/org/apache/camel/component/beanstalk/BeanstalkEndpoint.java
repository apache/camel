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
package org.apache.camel.component.beanstalk;

import com.surftools.BeanstalkClient.Client;
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
import org.apache.camel.impl.ScheduledPollEndpoint;

public class BeanstalkEndpoint extends ScheduledPollEndpoint {
    final ConnectionSettings conn;

    private String command = BeanstalkComponent.COMMAND_PUT;
    private long jobPriority = BeanstalkComponent.DEFAULT_PRIORITY;
    private int jobDelay = BeanstalkComponent.DEFAULT_DELAY;
    private int jobTimeToRun = BeanstalkComponent.DEFAULT_TIME_TO_RUN;

    public BeanstalkEndpoint(final String uri, final Component component, final ConnectionSettings conn) {
        super(uri, component);
        this.conn = conn;
    }

    public ConnectionSettings getConnection() {
        return conn;
    }

    public ConnectionSettings getConn() {
        return conn;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public long getJobPriority() {
        return jobPriority;
    }

    public void setJobPriority(long jobPriority) {
        this.jobPriority = jobPriority;
    }

    public int getJobDelay() {
        return jobDelay;
    }

    public void setJobDelay(int jobDelay) {
        this.jobDelay = jobDelay;
    }

    public int getJobTimeToRun() {
        return jobTimeToRun;
    }

    public void setJobTimeToRun(int jobTimeToRun) {
        this.jobTimeToRun = jobTimeToRun;
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
        if (BeanstalkComponent.COMMAND_PUT.equals(command)) {
            cmd = new PutCommand(this);
        } else if (BeanstalkComponent.COMMAND_RELEASE.equals(command)) {
            cmd = new ReleaseCommand(this);
        } else if (BeanstalkComponent.COMMAND_BURY.equals(command)) {
            cmd = new BuryCommand(this);
        } else if (BeanstalkComponent.COMMAND_TOUCH.equals(command)) {
            cmd = new TouchCommand(this);
        } else if (BeanstalkComponent.COMMAND_DELETE.equals(command)) {
            cmd = new DeleteCommand(this);
        } else if (BeanstalkComponent.COMMAND_KICK.equals(command)) {
            cmd = new KickCommand(this);
        } else {
            throw new IllegalArgumentException(String.format("Unknown command for Beanstalk endpoint: %s", command));
        }

        return new BeanstalkProducer(this, cmd);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        BeanstalkConsumer consumer = new BeanstalkConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
