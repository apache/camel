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
package org.apache.camel.bam;

import org.apache.camel.bam.model.TimerEvent;
import org.apache.camel.impl.ServiceSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.orm.jpa.JpaTemplate;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * @version $Revision: $
 */
public class TimerEngine extends ServiceSupport implements Runnable {
    private static final Log log = LogFactory.getLog(TimerEngine.class);

    private JpaTemplate template;
    private ExecutorService executor;
    private long windowMillis = 1000L;
    private Thread thread;

    public void run() {
        while (!isStopped()) {
            long nextPoll = System.currentTimeMillis() + windowMillis;

            Date window = new Date(nextPoll);
            List<TimerEvent> list = template.find("select x from " + TimerEvent.class.getName() + " where x.time < ?1 order by x.time", window);
            for (TimerEvent event : list) {
                fireEvent(event);
            }

            long timeToSleep = nextPoll - System.currentTimeMillis();
            if (timeToSleep > 0) {
                log.debug("Sleeping for " + timeToSleep + " millis");
                try {
                    Thread.sleep(timeToSleep);
                }
                catch (InterruptedException e) {
                    log.debug("Caught: " + e, e);
                }
            }
        }
    }

    protected void fireEvent(final TimerEvent event) {
        // lets try lock the object first

        template.execute(new JpaCallback() {
            public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                entityManager.lock(event, LockModeType.WRITE);
                event.fire();
                entityManager.remove(event);
                return null;
            }
        });

    }

    protected void doStart() throws Exception {
        thread = new Thread(this, "TimerEngine");
        thread.start();
    }

    protected void doStop() throws Exception {
        if (thread != null) {
            thread = null;
        }
    }
}
