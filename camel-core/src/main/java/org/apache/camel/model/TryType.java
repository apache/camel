/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.TryProcessor;
import org.apache.camel.processor.CatchProcessor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "try")
@XmlAccessorType(XmlAccessType.FIELD)
public class TryType extends ProcessorType {
    @XmlElement(required = false)
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();
    @XmlElementRef
    private List<CatchType> catchClauses = new ArrayList<CatchType>();
    @XmlElement(required = false)
    private FinallyType finallyClause;

    @Override
    public String toString() {
        return "Try[ " + getCatchClauses() + " " + getFinallyClause() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor tryProcessor = routeContext.createProcessor(this);
        Processor finallyProcessor = null;
        if (finallyClause != null) {
            finallyProcessor = finallyClause.createProcessor(routeContext);
        }
        List<CatchProcessor> catchProcessors = new ArrayList<CatchProcessor>();
        for (CatchType catchClause : catchClauses) {
            catchProcessors.add(catchClause.createProcessor(routeContext));
        }
        return new TryProcessor(tryProcessor, catchProcessors, finallyProcessor);
    }

    // Fluent API
    //-------------------------------------------------------------------------
    public TryType when(Class exceptionType) {
        getCatchClauses().add(new CatchType(exceptionType));
        return this;
    }

    public FinallyType otherwise() {
        FinallyType answer = new FinallyType();
        setFinallyClause(answer);
        return answer;
    }

    public TryType to(Endpoint endpoint) {
        super.to(endpoint);
        return this;
    }

    public TryType to(Collection<Endpoint> endpoints) {
        super.to(endpoints);
        return this;
    }

    public TryType to(Endpoint... endpoints) {
        super.to(endpoints);
        return this;
    }

    public TryType to(String uri) {
        super.to(uri);
        return this;
    }

    public TryType to(String... uris) {
        super.to(uris);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    public List<CatchType> getCatchClauses() {
        return catchClauses;
    }

    public void setCatchClauses(List<CatchType> catchClauses) {
        this.catchClauses = catchClauses;
    }

    public List<ProcessorType> getOutputs() {
        if (finallyClause != null) {
            return finallyClause.getOutputs();
        }
        else if (catchClauses.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        else {
            CatchType when = catchClauses.get(catchClauses.size() - 1);
            return when.getOutputs();
        }
    }

    public FinallyType getFinallyClause() {
        return finallyClause;
    }

    public void setFinallyClause(FinallyType finallyClause) {
        this.finallyClause = finallyClause;
    }

    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }
}