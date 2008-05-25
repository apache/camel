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
package org.apache.camel.processor.exceptionpolicy;

import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.model.ExceptionType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The default strategy used in Camel to resolve the {@link org.apache.camel.model.ExceptionType} that should
 * handle the thrown exception.
 * <p/>
 * This strategy applies the following rules:
 * <ul>
 *   <li>The exception type must be configured with an Exception that is an instance of the thrown exception</li>
 *   <li>If the exception type has exactly the thrown exception then its selected</li>
 *   <li>Otherwise the type that has an exception that is super of the thrown exception is selected
 *       (recurring up the exception hierarchy)
 *  </ul>
 */
public class DefaultExceptionPolicyStrategy implements ExceptionPolicyStrategy {

    private static final transient Log LOG = LogFactory.getLog(DefaultExceptionPolicyStrategy.class);

    public ExceptionType getExceptionPolicy(Map<Class, ExceptionType> exceptionPolicices, Exchange exchange,
                                            Throwable exception) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding best suited exception policy for thrown exception " + exception.getClass().getName());
        }

        // the goal is to find the exception with the same/closet inheritance level as the target exception being thrown
        int targetLevel = getInheritanceLevel(exception.getClass());
        // candidate is the best candidate found so far to return
        ExceptionType candidate = null;
        // difference in inheritance level between the current candidate and the thrown exception (target level)
        int candidateDiff = Integer.MAX_VALUE;

        // loop through all the entries and find the best candidates to use
        Set<Map.Entry<Class, ExceptionType>> entries = exceptionPolicices.entrySet();
        for (Map.Entry<Class, ExceptionType> entry : entries) {
            Class clazz = entry.getKey();
            ExceptionType type = entry.getValue();

            // must be instance of check to ensure that the clazz is one type of the thrown exception
            if (clazz.isInstance(exception)) {

                // exact match
                if (clazz.equals(exception.getClass())) {
                    candidate = type;
                    break;
                }

                // not an exact match so find the best candidate
                int level = getInheritanceLevel(clazz);
                int diff = targetLevel - level;

                if (diff < candidateDiff) {
                    // replace with a much better candidate
                    candidate = type;
                    candidateDiff = diff;
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            if (candidate != null) {
                LOG.debug("Using " + candidate + " as the exception policy");
            } else {
                LOG.debug("No candidate found to be used as exception policy");
            }
        }

        return candidate;
    }

    private static int getInheritanceLevel(Class clazz) {
        if (clazz == null || "java.lang.Object".equals(clazz.getName())) {
            return 0;
        }
        return 1 + getInheritanceLevel(clazz.getSuperclass());
    }

}
