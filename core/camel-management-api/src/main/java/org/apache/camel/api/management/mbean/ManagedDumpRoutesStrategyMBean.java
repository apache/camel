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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedDumpRoutesStrategyMBean {

    @ManagedOperation(description = "Dump routes in YAML or XML format.")
    void dumpRoutes(String format);

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Controls what to include in output. Possible values: routes, rests, routeTemplates. Multiple values can be separated by comma. Default is routes.")
    String getInclude();

    @ManagedAttribute(description = "Controls what to include in output. Possible values: routes, rests, routeTemplates. Multiple values can be separated by comma. Default is routes.")
    void setInclude(String include);

    @ManagedAttribute(description = "Whether to log route dumps to Logger")
    boolean isLog();

    @ManagedAttribute(description = "Whether to log route dumps to Logger")
    void setLog(boolean log);

    @ManagedAttribute(description = "Whether to save route dumps to file(s)")
    String getOutput();

    @ManagedAttribute(description = "Whether to save route dumps to file(s)")
    void setOutput(String output);

    @ManagedAttribute(description = "When dumping to YAML format, then this option controls whether endpoint URIs should be expanded into a key/value parameters")
    boolean isUriAsParameters();

    @ManagedAttribute(description = "When dumping to YAML format, then this option controls whether endpoint URIs should be expanded into a key/value parameters")
    void setUriAsParameters(boolean uriAsParameters);

    @ManagedAttribute(description = "")
    boolean isResolvePlaceholders();

    @ManagedAttribute(description = "Whether to resolve property placeholders in the dumped output. Default is true.")
    void setResolvePlaceholders(boolean resolvePlaceholders);

}
