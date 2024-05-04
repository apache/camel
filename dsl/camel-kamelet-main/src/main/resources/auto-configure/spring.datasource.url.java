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

var answer = "";
var registry = context.getRegistry();
answer = "Auto-configuring spring-datasource";

var p = new org.springframework.boot.autoconfigure.jdbc.DataSourceProperties();
p.setBeanClassLoader(context.getApplicationContextClassLoader());

var set = new org.apache.camel.util.OrderedLocationProperties();
var config = new org.apache.camel.util.OrderedLocationProperties();
var hikari = new org.apache.camel.util.OrderedLocationProperties();
// hikari is the default connection-pool used by spring-boot
hikari.putAll("camel-jbang", context.getPropertiesComponent().extractProperties("spring.datasource.hikari.", false));
config.putAll("camel-jbang", context.getPropertiesComponent().extractProperties("spring.datasource.", false));

org.apache.camel.main.MainHelper.setPropertiesOnTarget(context, p, config, "spring.datasource.", true, true, set);

var name = p.getName() != null ? p.getName() : "springDataSource";
var ds = p.initializeDataSourceBuilder().build();

// configure hikari connection-pool specific options
org.apache.camel.main.MainHelper.setPropertiesOnTarget(context, ds, hikari, "spring.datasource.hikari.", true, true, set);

// bind to registry
registry.bind(name, ds);

// log summary to see what was configured
org.apache.camel.main.MainHelper.logConfigurationSummary(null, set, "Spring DataSource (" + name + ")", null);

return answer;