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
package org.apache.camel.processor;

import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;

/**
 * A processor which catches exceptions.
 *
 * @version $Revision$
 */
public class CatchProcessor extends DelegateProcessor {
    private final List<Class> exceptions;
    private final Predicate onWhen;

    public CatchProcessor(List<Class> exceptions, Processor processor, Predicate onWhen) {
        super(processor);
        this.exceptions = exceptions;
        this.onWhen = onWhen;
    }

    @Override
    public String toString() {
        return "Catch[" + exceptions + " -> " + getProcessor() + "]";
    }

    public boolean catches(Exchange exchange, Throwable exception) {
        // use the exception iterator to walk the caused by hierachy
        Iterator<Throwable> it = ObjectHelper.createExceptionIterator(exception);
        while (it.hasNext()) {
            Throwable e = it.next();
            // see if we catch this type
            for (Class type : exceptions) {
                if (type.isInstance(e) && matchesWhen(exchange)) {
                    return true;
                }
            }
        }

        // not found
        return false;
    }

    public List<Class> getExceptions() {
        return exceptions;
    }

    /**
     * Strategy method for matching the exception type with the current exchange.
     * <p/>
     * This default implementation will match as:
     * <ul>
     * <li>Always true if no when predicate on the exception type
     * <li>Otherwise the when predicate is matches against the current exchange
     * </ul>
     *
     * @param exchange the current {@link org.apache.camel.Exchange}
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise.
     */
    protected boolean matchesWhen(Exchange exchange) {
        if (onWhen == null) {
            // if no predicate then it's always a match
            return true;
        }
        return onWhen.matches(exchange);
    }

}
