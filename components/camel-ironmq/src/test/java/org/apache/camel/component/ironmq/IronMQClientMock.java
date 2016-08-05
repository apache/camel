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
package org.apache.camel.component.ironmq;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.iron.ironmq.Client;
import io.iron.ironmq.Queue;

public class IronMQClientMock extends Client {
    private final Map<String, Queue> memQueues = new ConcurrentHashMap<String, Queue>();
    
    public IronMQClientMock(String projectId, String token) {
        super(projectId, token);
    }

    @Override
    public Queue queue(String name) {
        Queue answer = null;
        if (memQueues.containsKey(name)) {
            answer = memQueues.get(name);
        } else {
            answer = new MockQueue(this, name);
            memQueues.put(name, answer);
        }
        return answer;
    }

}
