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
package org.apache.camel.component.hdfs;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.xml.ws.Holder;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

public final class HdfsConsumer extends ScheduledPollConsumer {

    private final HdfsConfiguration config;
    private final StringBuilder hdfsPath;
    private final Processor processor;
    private AtomicBoolean idle = new AtomicBoolean(false);
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private HdfsInputStream istream;

    public HdfsConsumer(DefaultEndpoint endpoint, Processor processor, HdfsConfiguration config) {
        super(endpoint, processor);
        this.config = config;
        this.hdfsPath = config.getFileSystemType().getHdfsPath(config);
        this.processor = processor;
    }

    @Override
    protected void doStart() throws Exception {
        super.setInitialDelay(config.getInitialDelay());
        super.setDelay(config.getDelay());
        super.setUseFixedDelay(false);
        super.doStart();
    }

    @Override
    protected int poll() throws Exception {
        class ExcludePathFilter implements PathFilter {
            public boolean accept(Path path) {
                return !(path.toString().endsWith(config.getOpenedSuffix()) || path.toString().endsWith(config.getReadSuffix()));
            }
        }

        int numMessages = 0;

        HdfsInfo info = new HdfsInfo(this.hdfsPath.toString());
        FileStatus fileStatuses[];
        if (info.getFileSystem().isFile(info.getPath())) {
            fileStatuses = info.getFileSystem().globStatus(info.getPath());
        } else {
            Path pattern = info.getPath().suffix("/" + this.config.getPattern());
            fileStatuses = info.getFileSystem().globStatus(pattern, new ExcludePathFilter());
        }
        if (fileStatuses.length > 0) {
            this.idle.set(false);
        }
        for (int i = 0; i < fileStatuses.length; ++i) {
            try {
                this.rwlock.writeLock().lock();
                this.istream = HdfsInputStream.createInputStream(fileStatuses[i].getPath().toString(), this.config);
            } finally {
                this.rwlock.writeLock().unlock();
            }

            Holder<Object> key = new Holder<Object>();
            Holder<Object> value = new Holder<Object>();
            while (this.istream.next(key, value) != 0) {
                Exchange exchange = this.getEndpoint().createExchange();
                Message message = new DefaultMessage();
                if (key.value != null) {
                    message.setHeader(HdfsHeader.KEY.name(), key.value);
                }
                message.setBody(value.value);
                exchange.setIn(message);
                this.processor.process(exchange);
                numMessages++;
            }
            this.istream.close();
        }
        this.idle.set(true);
        return numMessages;
    }

    public HdfsInputStream getIstream() {
        try {
            rwlock.readLock().lock();
            return istream;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public AtomicBoolean isIdle() {
        return idle;
    }

}
