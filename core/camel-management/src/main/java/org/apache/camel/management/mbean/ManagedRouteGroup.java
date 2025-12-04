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

package org.apache.camel.management.mbean;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.TimerListener;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedRouteGroupMBean;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.TimeUtils;

@ManagedResource(description = "Managed Route Group")
public class ManagedRouteGroup extends ManagedPerformanceCounter implements TimerListener, ManagedRouteGroupMBean {

    public static final String VALUE_UNKNOWN = "Unknown";

    protected final String group;
    protected final CamelContext context;
    private final LoadTriplet load = new LoadTriplet();
    private final LoadThroughput thp = new LoadThroughput();
    private final String jmxDomain;

    public ManagedRouteGroup(CamelContext context, String group) {
        this.context = context;
        this.group = group;
        this.jmxDomain = context.getManagementStrategy().getManagementAgent().getMBeanObjectDomainName();
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean enabled = context.getManagementStrategy().getManagementAgent().getStatisticsLevel()
                != ManagementStatisticsLevel.Off;
        setStatisticsEnabled(enabled);
    }

    public CamelContext getContext() {
        return context;
    }

    @Override
    public String getRouteGroup() {
        return group;
    }

    @Override
    public int getGroupSize() {
        return context.getRoutesByGroup(group).size();
    }

    @Override
    public String[] getGroupIds() {
        List<String> list =
                context.getRoutesByGroup(group).stream().map(Route::getRouteId).toList();
        return list.toArray(new String[0]);
    }

    @Override
    public String getState() {
        String answer = null;
        for (Route route : context.getRoutesByGroup(group)) {
            ServiceStatus status = context.getRouteController().getRouteStatus(route.getId());
            if (status != null) {
                if (answer == null) {
                    answer = status.name();
                } else if (!status.name().equals(answer)) {
                    answer = VALUE_UNKNOWN;
                }
            }
        }
        return answer;
    }

    @Override
    public String getUptime() {
        long delta = getUptimeMillis();
        if (delta == 0) {
            return "";
        }
        return TimeUtils.printDuration(delta);
    }

    @Override
    public long getUptimeMillis() {
        long answer = -1;
        for (Route route : context.getRoutesByGroup(group)) {
            answer = Math.max(answer, route.getUptimeMillis());
        }
        return answer;
    }

    @Override
    public String getCamelId() {
        return context.getName();
    }

    @Override
    public String getCamelManagementName() {
        return context.getManagementName();
    }

    @Override
    public String getLoad01() {
        double load1 = load.getLoad1();
        if (Double.isNaN(load1)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load1);
        }
    }

    @Override
    public String getLoad05() {
        double load5 = load.getLoad5();
        if (Double.isNaN(load5)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load5);
        }
    }

    @Override
    public String getLoad15() {
        double load15 = load.getLoad15();
        if (Double.isNaN(load15)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load15);
        }
    }

    @Override
    public String getThroughput() {
        double d = thp.getThroughput();
        if (Double.isNaN(d)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", d);
        }
    }

    @Override
    public void onTimer() {
        load.update(getInflightExchanges());
        thp.update(getExchangesTotal());
    }

    private Integer getInflightExchanges() {
        return (int) super.getExchangesInflight();
    }

    @Override
    public void start() throws Exception {
        context.getRouteController().startRouteGroup(group);
    }

    @Override
    public void stop() throws Exception {
        context.getRouteController().stopRouteGroup(group);
    }
}
