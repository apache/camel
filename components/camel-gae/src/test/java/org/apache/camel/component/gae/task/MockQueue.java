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
package org.apache.camel.component.gae.task;

import java.util.Map;

import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.labs.taskqueue.Queue;
import com.google.appengine.api.labs.taskqueue.TaskHandle;
import com.google.appengine.api.labs.taskqueue.TaskOptions;
import com.google.appengine.api.labs.taskqueue.TaskOptionsAccessor;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.servletunit.ServletUnitClient;

import org.junit.Assert;

public class MockQueue implements Queue {

    private String name;

    private ServletUnitClient servletUnitClient;
    
    public MockQueue() {
        this("default");
    }
    
    public MockQueue(String name) {
        this.name = name;
    }
    
    public void setServletUnitClient(ServletUnitClient servletUnitClient) {
        this.servletUnitClient = servletUnitClient;
    }

    public TaskHandle add() {
        throw new UnsupportedOperationException("not implemented");
    }

    public TaskHandle add(TaskOptions taskOptions) {
        return add(null, taskOptions);
    }

    public TaskHandle add(Transaction transaction, TaskOptions taskOptions) {
        TaskOptionsAccessor accessor = new TaskOptionsAccessor(taskOptions);
        try {
            PostMethodWebRequest request = new PostMethodWebRequest(accessor.getUrl(), accessor.getPayload(), null);
            request.setHeaderField(GTaskBinding.GAE_QUEUE_NAME, name);
            request.setHeaderField(GTaskBinding.GAE_RETRY_COUNT, "0");
            if (accessor.getTaskName() != null) {
                request.setHeaderField(GTaskBinding.GAE_TASK_NAME, accessor.getTaskName());
            }
            for (Map.Entry<String, String> entry : accessor.getHeaders().entrySet()) {
                request.setHeaderField(entry.getKey(), entry.getValue());
            }
            servletUnitClient.getResponse(request);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
        return null;
    }

    public String getQueueName() {
        return name;
    }

}
