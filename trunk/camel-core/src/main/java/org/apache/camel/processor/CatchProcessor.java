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
 * @version 
 */
public class CatchProcessor extends DelegateAsyncProcessor implements Traceable {
    private final List<Class> exceptions;
    private final Predicate onWhen;
    private final Predicate handled;

    public CatchProcessor(List<Class> exceptions, Processor processor, Predicate onWhen, Predicate handled) {
        super(processor);
        this.exceptions = exceptions;
        this.onWhen = onWhen;
        this.handled = handled;
    }

    @Override
    public String toString() {
        return "Catch[" + exceptions + " -> " + getProcessor() + "]";
    }

    public String getTraceLabel() {
        return "catch";
    }

    /**
     * Returns with the exception that is caught by this processor.
     * 
     * This method traverses exception causes, so sometimes the exception
     * returned from this method might be one of causes of the parameter
     * passed.
     *
     * @param exchange  the current exchange
     * @param exception the thrown exception
     * @return Throwable that this processor catches. <tt>null</tt> if nothing matches.
     */
    public Throwable catches(Exchange exchange, Throwable exception) {
        // use the exception iterator to walk the caused by hierarchy
        Iterator<Throwable> it = ObjectHelper.createExceptionIterator(exception);
        while (it.hasNext()) {
            Throwable e = it.next();
            // see if we catch this type
            for (Class<?> type : exceptions) {
                if (type.isInstance(e) && matchesWhen(exchange)) {
                    return e;
                }
            }
        }

        // not found
        return null;
    }


    /**
     * Whether this catch processor handles the exception it have caught
     *
     * @param exchange  the current exchange
     * @return <tt>true</tt> if this processor handles it, <tt>false</tt> otherwise.
     */
    public boolean handles(Exchange exchange) {
        if (handled == null) {
            // handle by default
            return true;
        }

        return handled.matches(exchange);
    }

    public List<Class> getExceptions() {
        return exceptions;
    }

    /**
     * Strategy method for matching the exception type with the current exchange.
     * <p/>
     * This default implementation will match as:
     * <ul>
     *   <li>Always true if no when predicate on the exception type
     *   <li>Otherwise the when predicate is matches against the current exchange
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
