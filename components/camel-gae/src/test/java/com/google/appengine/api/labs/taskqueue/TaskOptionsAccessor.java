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
package com.google.appengine.api.labs.taskqueue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class TaskOptionsAccessor {

    private TaskOptions taskOptions;
    
    public TaskOptionsAccessor(TaskOptions taskOptions) {
        this.taskOptions = taskOptions;
    }
    
    public TaskOptions getTaskOptions() {
        return taskOptions;
    }
    
    public String getUrl() {
        return "http://localhost" + getPath();
    }
    
    public String getPath() {
        return taskOptions.getUrl();
    }
    
    public Map<String, List<String>> getHeaders() {
        return taskOptions.getHeaders();
    }
    
    public InputStream getPayload() {
        return new ByteArrayInputStream(taskOptions.getPayload());
    }

    public String getTaskName() {
        return taskOptions.getTaskName();
    }
    
}
