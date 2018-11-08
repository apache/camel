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
package org.apache.camel.opentracing.starter;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.opentracing")
public class OpenTracingConfigurationProperties {

    /**
     * Sets exclude pattern(s) that will disable tracing for Camel messages that
     * matches the pattern.
     */
    private Set<String> excludePatterns;
    /**
     * Activate or deactivate dash encoding in headers (required by JMS) for
     * messaging
     */
    private Boolean encoding;

    public Set<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(Set<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public Boolean getEncoding() {
        return encoding;
    }

    public void setEncoding(Boolean encoding) {
        this.encoding = encoding;
    }
}
