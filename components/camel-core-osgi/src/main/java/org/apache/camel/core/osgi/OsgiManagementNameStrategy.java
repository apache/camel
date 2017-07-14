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
package org.apache.camel.core.osgi;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultManagementNameStrategy;
import org.osgi.framework.BundleContext;

/**
 * OSGi enhanced {@link org.apache.camel.spi.ManagementNameStrategy}.
 * <p/>
 * This {@link org.apache.camel.spi.ManagementNameStrategy} supports the default
 * tokens (see {@link DefaultManagementNameStrategy}) and the following additional OSGi specific tokens
 * <ul>
 *     <li>#bundleId# - The bundle id</li>
 *     <li>#version# - The bundle version</li>
 *     <li>#symbolicName# - The bundle symbolic name</li>
 * </ul>
 * <p/>
 * This implementation will by default use a name pattern as <tt>#symbolicName#</tt> and in case
 * of a clash (such as multiple versions of the same symbolicName),
 * then the pattern will fallback to append an unique counter <tt>#symbolicName#-#counter#</tt>.
 *
 * @see DefaultManagementNameStrategy
 */
public class OsgiManagementNameStrategy extends DefaultManagementNameStrategy {

    private static final AtomicInteger CONTEXT_COUNTER = new AtomicInteger(0);
    private final BundleContext bundleContext;

    public OsgiManagementNameStrategy(CamelContext camelContext, BundleContext bundleContext) {
        super(camelContext, "#symbolicName#-#name#", "#symbolicName#-#name#-#counter#");
        this.bundleContext = bundleContext;
    }

    @Override
    protected String customResolveManagementName(String pattern, String answer) {
        String bundleId = "" + bundleContext.getBundle().getBundleId();
        String symbolicName = bundleContext.getBundle().getSymbolicName();
        if (symbolicName != null) {
            symbolicName = Matcher.quoteReplacement(symbolicName);
        } else {
            symbolicName = "";
        }
        String version = Matcher.quoteReplacement(bundleContext.getBundle().getVersion().toString());

        answer = answer.replaceFirst("#bundleId#", bundleId);
        answer = answer.replaceFirst("#symbolicName#", symbolicName);
        answer = answer.replaceFirst("#version#", version);

        // we got a candidate then find a free name
        // true = check fist if the candidate as-is is free, if not then use the counter
        answer = OsgiNamingHelper.findFreeCamelContextName(bundleContext, answer, OsgiCamelContextPublisher.CONTEXT_MANAGEMENT_NAME_PROPERTY, CONTEXT_COUNTER, true);

        return answer;
    }
    
}
