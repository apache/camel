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

package org.apache.camel.dsl.jbang.core.model;

import java.util.Map;

import org.apache.camel.util.json.JsonObject;

public class ListProcessDTO {

    private int pid;
    private String name;
    private String ready;
    private String status;
    private String age;
    private long total;
    private long fail;
    private int inflight;

    public ListProcessDTO() {}

    public ListProcessDTO(
            String pid,
            String name,
            String ready,
            String status,
            String age,
            String total,
            String fail,
            String inflight) {
        this.pid = Integer.parseInt(pid);
        this.name = name;
        this.ready = ready;
        this.status = status;
        this.age = age;
        this.total = Long.parseLong(total);
        this.fail = Long.parseLong(fail);
        this.inflight = Integer.parseInt(inflight);
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReady() {
        return ready;
    }

    public void setReady(String ready) {
        this.ready = ready;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getFail() {
        return fail;
    }

    public void setFail(long fail) {
        this.fail = fail;
    }

    public int getInflight() {
        return inflight;
    }

    public void setInflight(int inflight) {
        this.inflight = inflight;
    }

    public Map<String, Object> toMap() {
        JsonObject jo = new JsonObject();
        jo.put("pid", pid);
        jo.put("name", name);
        jo.put("ready", ready);
        jo.put("status", status);
        jo.put("age", age);
        jo.put("total", total);
        jo.put("fail", fail);
        jo.put("inflight", inflight);
        return jo;
    }
}
