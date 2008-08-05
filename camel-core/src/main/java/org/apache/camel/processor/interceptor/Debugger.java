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
package org.apache.camel.processor.interceptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An interceptor strategy for debugging and tracing routes
 *
 * @version $Revision$
 */
public class Debugger implements InterceptStrategy {
    private static final transient Log LOG = LogFactory.getLog(Debugger.class);

    private int exchangeBufferSize = -1;
    private Map<String, DebugInterceptor> interceptors = new HashMap<String, DebugInterceptor>();
    private boolean logExchanges = true;
    private boolean enabled = true;
    private Tracer tracer = new Tracer();


    /**
     * A helper method to return the debugger instance for a given {@link CamelContext} if one is enabled
     *
     * @param context the camel context the debugger is connected to
     * @return the debugger or null if none can be found
     */
    public static Debugger getDebugger(CamelContext context) {
        if (context instanceof DefaultCamelContext) {
            DefaultCamelContext defaultCamelContext = (DefaultCamelContext) context;
            List<InterceptStrategy> list = defaultCamelContext.getInterceptStrategies();
            for (InterceptStrategy interceptStrategy : list) {
                if (interceptStrategy instanceof Debugger) {
                    return (Debugger)interceptStrategy;
                }
            }
        }
        return null;
    }


    public DebugInterceptor getInterceptor(String id) {
        return interceptors.get(id);
    }


    /**
     * Returns the list of exchanges sent to the given node in the DSL
     */
    public List<Exchange> getExchanges(String id) {
        DebugInterceptor interceptor = getInterceptor(id);
        if (interceptor == null) {
            return null;
        } else {
            return interceptor.getExchanges();
        }
    }

    public void setEnable(boolean flag) {
        enabled = flag;
        tracer.setEnabled(flag);
        for (DebugInterceptor interceptor : interceptors.values()) {
            interceptor.setEnabled(flag);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the breakpoint object for the given node in the DSL
     */
    public Breakpoint getBreakpoint(String id) {
        DebugInterceptor interceptor = getInterceptor(id);
        if (interceptor == null) {
            return null;
        } else {
            return interceptor.getBreakpoint();
        }
    }

    public TraceFormatter getTraceFormatter() {
        return tracer.getFormatter();
    }

    public void setTraceFormatter(TraceFormatter formatter) {
        tracer.setFormatter(formatter);
    }

    public void setLogExchanges(boolean flag) {
        logExchanges = flag;
    }


    public Processor wrapProcessorInInterceptors(ProcessorType processorType, Processor target) throws Exception {
        String id = processorType.idOrCreate();
        if (logExchanges) {
            TraceInterceptor  traceInterceptor = new TraceInterceptor(processorType, target, tracer);
            target = traceInterceptor;
        }
        DebugInterceptor interceptor = new DebugInterceptor(processorType, target, createExchangeList(), createExceptionsList());
        interceptors.put(id, interceptor);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding " + id + " interceptor: " + interceptor);
        }
        return interceptor;
    }

    protected List<Exchange> createExchangeList() {
        if (exchangeBufferSize == 0) {
            return null;
        } else if (exchangeBufferSize > 0) {
            // TODO lets create a non blocking fixed size queue
            return new ArrayList<Exchange>();
        } else {
            return new ArrayList<Exchange>();
        }
    }

    protected List<ExceptionEvent> createExceptionsList() {
        // TODO allow some kinda LRU based fixed size list to be used?
        return new ArrayList<ExceptionEvent>();
    }


}
