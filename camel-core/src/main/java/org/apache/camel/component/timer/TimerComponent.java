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
package org.apache.camel.component.timer;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultComponent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

/**
 * Represents the component that manages {@link TimerEndpoint}.  It holds the
 * list of {@link TimerConsumer} objects that are started.
 *
 * @version $Revision: 519973 $
 */
public class TimerComponent extends DefaultComponent<Exchange> {
    private Map<String, Timer> timers = new HashMap<String, Timer>();

    public Timer getTimer(TimerEndpoint endpoint) {
        String key = endpoint.getTimerName();
        if (! endpoint.isDaemon()) {
           key = "nonDaemon:" + key;
        }

        Timer answer = timers.get(key);
        if (answer == null) {
            answer = new Timer(endpoint.getTimerName(), endpoint.isDaemon());
            timers.put(key, answer);
        }
        return answer;
    }
    
    @Override
    protected Endpoint<Exchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        TimerEndpoint answer = new TimerEndpoint(uri, this, remaining);
        setProperties(answer, parameters);
        return answer;
    }

    @Override
    protected void doStop() throws Exception {
        Collection<Timer> collection = timers.values();
        for (Timer timer : collection) {
            timer.cancel();
        }
        timers.clear();
    }
}
