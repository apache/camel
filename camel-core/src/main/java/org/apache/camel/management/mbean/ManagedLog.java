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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedLogMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.LogProcessor;
import org.slf4j.Marker;

/**
 * @version 
 */
@ManagedResource(description = "Managed Log")
public class ManagedLog extends ManagedProcessor implements ManagedLogMBean {
    private final LogProcessor processor;

    public ManagedLog(CamelContext context, LogProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public String getMessage() {
        return processor.getExpression().toString();
    }

    @Override
    public String getLoggingLevel() {
        LoggingLevel level = processor.getLogger().getLevel();
        return level != null ? level.name() : null;
    }

    @Override
    public String getLogName() {
        return processor.getLogger().getLog().getName();
    }

    @Override
    public String getMarker() {
        Marker marker = processor.getLogger().getMarker();
        return marker != null ? marker.getName() : null;
    }
}
