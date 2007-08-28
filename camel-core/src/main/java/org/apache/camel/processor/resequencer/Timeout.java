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
package org.apache.camel.processor.resequencer;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A timer task that notifies handlers about scheduled timeouts.
 * 
 * @see Timer
 * @see TimerTask
 * 
 * @author Martin Krasser
 * 
 * @version $Revision
 */
public class Timeout extends TimerTask {
    
    private List<TimeoutHandler> timeoutHandlers;
    
    private Timer timer;
    
    private long timeout;
    
    /**
     * Creates a new timeout task using the given {@link Timer} instance a timeout value. The
     * task is not scheduled immediately. It will be scheduled by calling this
     * task's {@link #schedule()} method.
     * 
     * @param timer
     * @param timeout
     */
    public Timeout(Timer timer, long timeout) {
        this.timeoutHandlers = new LinkedList<TimeoutHandler>();
        this.timeout = timeout;
        this.timer = timer;
    }

    /**
     * Returns the list of timeout handlers that have been registered for
     * notification.
     * 
     * @return the list of timeout handlers
     */
    public List<TimeoutHandler> getTimeoutHandlers() {
        return timeoutHandlers;
    }
    
    /**
     * Appends a new timeout handler at the end of the timeout handler list.
     * 
     * @param handler a timeout handler.
     */
    public void addTimeoutHandler(TimeoutHandler handler) {
        timeoutHandlers.add(handler);
    }
    
    /**
     * inserts a new timeout handler at the beginning of the timeout handler
     * list.
     * 
     * @param handler a timeout handler.
     */
    public void addTimeoutHandlerFirst(TimeoutHandler handler) {
        timeoutHandlers.add(0, handler);
    }
    
    /**
     * Removes all timeout handlers from the timeout handler list. 
     */
    public void clearTimeoutHandlers() {
        this.timeoutHandlers.clear();
    }
    
    /**
     * Schedules this timeout task.
     */
    public void schedule() {
        timer.schedule(this, timeout);
    }

    /**
     * Notifies all timeout handlers about the scheduled timeout.
     */
    @Override
    public void run() {
        for (TimeoutHandler observer : timeoutHandlers) {
            observer.timeout(this);
        }
    }

}
