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
import org.apache.camel.Producer;
import org.apache.camel.component.beanstalk.processors.*;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollEndpoint;

/**
 * @author <a href="mailto:azarov@osinka.com">Alexander Azarov</a>
 * @see BeanstalkConsumer
 * @see org.apache.camel.component.beanstalk.processors.PutCommand
 */
public class BeanstalkEndpoint extends ScheduledPollEndpoint {
    final ConnectionSettings conn;

    String command      = BeanstalkComponent.COMMAND_PUT;
    long priority       = BeanstalkComponent.DEFAULT_PRIORITY;
    int delay           = BeanstalkComponent.DEFAULT_DELAY;
    int timeToRun       = BeanstalkComponent.DEFAULT_TIME_TO_RUN;

    BeanstalkEndpoint(final String uri, final Component component, final ConnectionSettings conn) {
        super(uri, component);

        this.conn = conn;
    }

    public ConnectionSettings getConnection() {
        return conn;
    }

    /**
     * The command {@link Producer} must execute
     *
     * @param command
     */
    public void setCommand(final String command) {
        this.command = command;
    }

    public void setJobPriority(final long priority) {
        this.priority = priority;
    }

    public long getJobPriority() {
        return priority;
    }

    public void setJobDelay(final int delay) {
        this.delay = delay;
    }

    public int getJobDelay() {
        return delay;
    }

    public void setJobTimeToRun(final int timeToRun) {
        this.timeToRun = timeToRun;
    }

    public int getJobTimeToRun() {
        return timeToRun;
    }

    /**
     * Creates Camel producer.
     * <p>
     * Depending on the command parameter (see {@link BeanstalkComponent} URI) it
     * will create one of the producer implementations.
     *
     * @return {@link Producer} instance
     * @throws IllegalArgumentException when {@link ConnectionSettings} cannot
     * create a writable {@link Client}
     */
    @Override
    public Producer createProducer() throws Exception {
        Command cmd = null;
        if (BeanstalkComponent.COMMAND_PUT.equals(command))
            cmd = new PutCommand(this);
        else if (BeanstalkComponent.COMMAND_RELEASE.equals(command))
            cmd = new ReleaseCommand(this);
        else if (BeanstalkComponent.COMMAND_BURY.equals(command))
            cmd = new BuryCommand(this);
        else if (BeanstalkComponent.COMMAND_TOUCH.equals(command))
            cmd = new TouchCommand(this);
        else if (BeanstalkComponent.COMMAND_DELETE.equals(command))
            cmd = new DeleteCommand(this);
        else if (BeanstalkComponent.COMMAND_KICK.equals(command))
            cmd = new KickCommand(this);
        else
            throw new IllegalArgumentException(String.format("Unknown command for Beanstalk endpoint: %s", command));

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
