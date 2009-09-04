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

package org.apache.camel.web.util;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.model.AOPDefinition;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.OutputDefinition;
import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RemoveHeaderDefinition;
import org.apache.camel.model.RemovePropertyDefinition;
import org.apache.camel.model.SetExchangePatternDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.model.StopDefinition;
import org.apache.camel.model.ThreadsDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.UnmarshalDefinition;

/**
 *
 */
public final class OutputDefinitionRenderer {
    private OutputDefinitionRenderer() {
        // Utility class, no public or protected default constructor
    }

    public static void render(StringBuilder buffer, ProcessorDefinition<?> processor) {
        OutputDefinition out = (OutputDefinition)processor;
        boolean notGlobal = buffer.toString().endsWith(")");
        if (notGlobal) {
            buffer.append(".");
        }
        buffer.append(out.getShortName());

        if (out instanceof AOPDefinition) {
            renderAop(buffer, out);
        } else if (out instanceof BeanDefinition) {
            renderBean(buffer, processor);
        } else if (out instanceof EnrichDefinition) {
            String enrich = out.toString();
            String resourceUri = enrich.substring(enrich.indexOf('[') + 1, enrich.indexOf(' '));
            buffer.append("(\"").append(resourceUri).append("\")");
        } else if (out instanceof FinallyDefinition) {
            renderFinally(buffer, out);
        } else if (out instanceof InterceptDefinition) {
            if (out instanceof InterceptFromDefinition) {
                InterceptFromDefinition interceptFrom = (InterceptFromDefinition)out;
                if (interceptFrom.getUri() != null) {
                    buffer.append("(\"").append(interceptFrom.getUri()).append("\")");
                    return;
                }
            }
            buffer.append("()");
        } else if (out instanceof InterceptSendToEndpointDefinition) {
            InterceptSendToEndpointDefinition interceptSend = (InterceptSendToEndpointDefinition)out;
            buffer.append("(\"").append(interceptSend.getUri()).append("\")");
            if (interceptSend.getSkipSendToOriginalEndpoint()) {
                buffer.append(".skipSendToOriginalEndpoint()");
            }
        } else if (out instanceof MarshalDefinition) {
            DataFormatDefinition dataFormat = ((MarshalDefinition)out).getDataFormatType();
            XmlRootElement xmlRoot = dataFormat.getClass().getAnnotation(XmlRootElement.class);
            buffer.append("().").append(xmlRoot.name()).append("()");
        } else if (out instanceof MulticastDefinition) {
            buffer.append("()");
        } else if (out instanceof OtherwiseDefinition) {
            buffer.append("()");
        } else if (out instanceof PipelineDefinition) {
            // transformed into simple ToDefinition
        } else if (out instanceof PolicyDefinition) {
            renderPolicy(buffer, out);
        } else if (out instanceof PollEnrichDefinition) {
            renderPollEnrich(buffer, out);
        } else if (out instanceof ProcessDefinition) {
            renderProcess(buffer, out);
        } else if (out instanceof RemoveHeaderDefinition) {
            RemoveHeaderDefinition remove = (RemoveHeaderDefinition)out;
            buffer.append("(\"").append(remove.getHeaderName()).append("\")");
        } else if (out instanceof RemovePropertyDefinition) {
            RemovePropertyDefinition remove = (RemovePropertyDefinition)out;
            buffer.append("(\"").append(remove.getPropertyName()).append("\")");
        } else if (out instanceof SetExchangePatternDefinition) {
            SetExchangePatternDefinition setEP = (SetExchangePatternDefinition)out;
            buffer.append("(ExchangePattern.");
            buffer.append(setEP.getPattern().toString());
            buffer.append(")");
        } else if (out instanceof SortDefinition) {
            SortDefinition sort = (SortDefinition)out;
            buffer.append("(");
            ExpressionRenderer.renderExpression(buffer, sort.getExpression().toString());
            buffer.append(")");
        } else if (out instanceof StopDefinition) {
            buffer.append("()");
        } else if (out instanceof ThreadsDefinition) {
            renderThreads(buffer, out);
        } else if (out instanceof TransactedDefinition) {
            renderTransacted(buffer, out);
        } else if (out instanceof TryDefinition) {
            buffer.append("()");
        } else if (out instanceof UnmarshalDefinition) {
            DataFormatDefinition dataFormat = ((UnmarshalDefinition)out).getDataFormatType();
            XmlRootElement xmlRoot = dataFormat.getClass().getAnnotation(XmlRootElement.class);
            buffer.append("().").append(xmlRoot.name()).append("()");
        }
    }

    private static void renderAop(StringBuilder buffer, OutputDefinition out) {
        buffer.append("()");
        AOPDefinition aop = (AOPDefinition)out;
        if (aop.getBeforeUri() != null) {
            if (aop.getAfterUri() != null) {
                buffer.append(".around(\"").append(aop.getBeforeUri());
                buffer.append("\", \"").append(aop.getAfterUri()).append("\")");
            } else if (aop.getAfterFinallyUri() != null) {
                buffer.append(".aroundFinally(\"").append(aop.getBeforeUri());
                buffer.append("\", \"").append(aop.getAfterFinallyUri()).append("\")");
            } else {
                buffer.append(".before(\"").append(aop.getBeforeUri()).append("\")");
            }
        } else if (aop.getAfterUri() != null) {
            buffer.append(".after(\"").append(aop.getAfterUri()).append("\")");
        } else if (aop.getAfterFinallyUri() != null) {
            buffer.append(".afterFinally(\"").append(aop.getAfterFinallyUri()).append("\")");
        }
    }

    private static void renderBean(StringBuilder buffer, ProcessorDefinition<?> processor) {
        BeanDefinition beanDef = (BeanDefinition)processor;
        if (beanDef.getRef() != null) {
            buffer.append("Ref(\"").append(beanDef.getRef()).append("\"");
            if (beanDef.getMethod() != null) {
                buffer.append(", \"").append(beanDef.getMethod()).append("\"");
            }
            buffer.append(")");
        }
    }

    private static void renderFinally(StringBuilder buffer, OutputDefinition out) {
        buffer.append("()");
        FinallyDefinition finallyDef = (FinallyDefinition)out;
        List<ProcessorDefinition> branches = finallyDef.getOutputs();
        for (ProcessorDefinition branch : branches) {
            SendDefinitionRenderer.render(buffer, branch);
        }
        buffer.append(".end()");
    }

    private static void renderPolicy(StringBuilder buffer, OutputDefinition out) {
        PolicyDefinition policy = (PolicyDefinition)out;
        buffer.append("(");
        if (policy.getRef() != null) {
            buffer.append("\"").append(policy.getRef()).append("\"");
        }
        buffer.append(")");
    }

    private static void renderPollEnrich(StringBuilder buffer, OutputDefinition out) {
        PollEnrichDefinition pollEnrich = (PollEnrichDefinition)out;
        buffer.append("(\"");
        buffer.append(pollEnrich.getResourceUri()).append("\", ").append(pollEnrich.getTimeout());
        if (pollEnrich.getAggregationStrategy() != null) {
            buffer.append(", An aggregationStrategy instance here");
        }
        buffer.append(")");
    }

    private static void renderProcess(StringBuilder buffer, OutputDefinition out) {
        ProcessDefinition process = (ProcessDefinition)out;
        if (process.getRef() != null) {
            buffer.append("Ref(\"").append(process.getRef()).append("\")");
        } else {
            buffer.append("(");
            buffer.append("An inlined processor instance here");
            buffer.append(")");
        }
    }

    private static void renderThreads(StringBuilder buffer, OutputDefinition out) {
        ThreadsDefinition threads = (ThreadsDefinition)out;
        buffer.append("(");
        if (threads.getPoolSize() != null) {
            buffer.append(threads.getPoolSize());
        }
        buffer.append(")");

        WaitForTaskToComplete wait = threads.getWaitForTaskToComplete();
        if (wait != WaitForTaskToComplete.IfReplyExpected) {
            buffer.append(".waitForTaskToComplete(WaitForTaskToComplete.").append(wait).append(")");
        }
    }

    private static void renderTransacted(StringBuilder buffer, OutputDefinition out) {
        TransactedDefinition transacted = (TransactedDefinition)out;
        buffer.append("(");
        if (transacted.getRef() != null) {
            buffer.append("\"").append(transacted.getRef()).append("\"");
        }
        buffer.append(")");
    }
}
