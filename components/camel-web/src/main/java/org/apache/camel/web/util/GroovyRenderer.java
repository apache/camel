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

import java.io.IOException;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.OutputDefinition;
import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RemoveHeaderDefinition;
import org.apache.camel.model.RemovePropertyDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SetExchangePatternDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetOutHeaderDefinition;
import org.apache.camel.model.SetPropertyDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.StopDefinition;
import org.apache.camel.model.ThreadsDefinition;
import org.apache.camel.model.ThrottleDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.loadbalancer.FailOverLoadBalancer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RandomLoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.processor.loadbalancer.StickyLoadBalancer;
import org.apache.camel.processor.loadbalancer.TopicLoadBalancer;

/**
 * Render routes in Groovy language
 */
public class GroovyRenderer implements TextRenderer {

    public static final String header = "import org.apache.camel.language.groovy.GroovyRouteBuilder;\nclass GroovyRoute extends GroovyRouteBuilder {\nvoid configure() {\n";

    public static final String footer = "\n}\n}";

    /**
     * render a RouteDefinition
     * 
     * @throws IOException
     */
    public void renderRoute(StringBuilder buffer, RouteDefinition route) {
        // TODO Auto-generated method stub
        List<FromDefinition> inputs = route.getInputs();
        List<ProcessorDefinition> outputs = route.getOutputs();

        // render the error handler
        if (!(route.getErrorHandlerBuilder() instanceof ErrorHandlerBuilderRef)) {
            if (route.getErrorHandlerBuilder() instanceof DeadLetterChannelBuilder) {
                DeadLetterChannelBuilder deadLetter = (DeadLetterChannelBuilder)route.getErrorHandlerBuilder();
                buffer.append("errorHandler(deadLetterChannel(\"").append(deadLetter.getDeadLetterUri()).append("\")");
                buffer.append(".maximumRedeliveries(").append(deadLetter.getRedeliveryPolicy().getMaximumRedeliveries()).append(")");
                buffer.append(".redeliverDelay(").append(deadLetter.getRedeliveryPolicy().getRedeliverDelay()).append(")");
                buffer.append(".handled(").append(deadLetter.getHandledPolicy().toString()).append(")");
                buffer.append(");");
            }
        }

        // render the inputs of the router
        buffer.append("from(");
        for (FromDefinition input : inputs) {
            buffer.append("\"").append(input.getUri()).append("\"");
            if (input != inputs.get(inputs.size() - 1)) {
                buffer.append(", ");
            }
        }
        buffer.append(")");

        // render the outputs of the router
        for (ProcessorDefinition processor : outputs) {
            renderProcessor(buffer, processor);
        }
    }

    /**
     * render a RoutesDefinition
     */
    public void renderRoutes(StringBuilder buffer, RoutesDefinition routes) {
        // TODO Auto-generated method stub

    }

    /**
     * render a ProcessorDefiniton
     */
    private void renderProcessor(StringBuilder buffer, ProcessorDefinition processor) {
        if (processor instanceof AggregateDefinition) {
            AggregateDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof ChoiceDefinition) {
            ChoiceDefinition choice = (ChoiceDefinition)processor;
            buffer.append(".").append(choice.getShortName()).append("()");
            for (WhenDefinition when : choice.getWhenClauses()) {
                renderProcessor(buffer, when);
            }
            OtherwiseDefinition other = choice.getOtherwise();
            if (other != null) {
                renderProcessor(buffer, other);
            }
            buffer.append(".end()");
            return;
        } else if (processor instanceof ConvertBodyDefinition) {
            ConvertBodyDefinition convertBody = (ConvertBodyDefinition)processor;
            buffer.append(".").append(convertBody.getShortName()).append("(");
            if (convertBody.getType().equals("[B")) {
                buffer.append("byte[].class");
            } else {
                buffer.append(convertBody.getType()).append(".class");
            }
            if (convertBody.getCharset() != null) {
                buffer.append(", \"").append(convertBody.getCharset()).append("\"");
            }
            buffer.append(")");
        } else if (processor instanceof ExpressionNode) {
            ExpressionNodeRenderer.render(buffer, processor);
        } else if (processor instanceof LoadBalanceDefinition) {
            LoadBalanceDefinition loadB = (LoadBalanceDefinition)processor;
            // buffer.append(".").append(output.getShortName()).append("()");
            buffer.append(".").append("loadBalance").append("()");

            LoadBalancer lb = loadB.getLoadBalancerType().getLoadBalancer(null);
            if (lb instanceof FailOverLoadBalancer) {
                buffer.append(".failover()");
            } else if (lb instanceof RandomLoadBalancer) {
                buffer.append(".random()");
            } else if (lb instanceof RoundRobinLoadBalancer) {
                buffer.append(".roundRobin()");
            } else if (lb instanceof StickyLoadBalancer) {
                buffer.append(".sticky()");
            } else if (lb instanceof TopicLoadBalancer) {
                buffer.append(".topic()");
            }

            List<ProcessorDefinition> branches = loadB.getOutputs();
            for (ProcessorDefinition branch : branches) {
                renderProcessor(buffer, branch);
            }
            return;
        } else if (processor instanceof OutputDefinition) {
            OutputDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof ResequenceDefinition) {
            ResequenceDefinition resequence = (ResequenceDefinition)processor;
            buffer.append(".").append(processor.getShortName()).append("(");
            List<ExpressionBuilder> exps = null;
            for (ExpressionBuilder exp : exps) {
                buffer.append(exp.toString()).append("(),");
            }
            buffer.append(")");
        } else if (processor instanceof RoutingSlipDefinition) {
            RoutingSlipDefinition routingSlip = (RoutingSlipDefinition)processor;
            buffer.append(".").append(routingSlip.getShortName()).append("(\"").append(routingSlip.getHeaderName()).append("\", \"").append(routingSlip.getUriDelimiter())
                .append("\")");
        } else if (processor instanceof SendDefinition) {
            SendDefinitionRenderer.render(buffer, processor);
        } else if (processor instanceof ThrottleDefinition) {
            ThrottleDefinition throttle = (ThrottleDefinition)processor;
            buffer.append(".").append(throttle.getShortName()).append("(").append(throttle.getMaximumRequestsPerPeriod()).append(")");
            if (throttle.getTimePeriodMillis() != 1000) {
                buffer.append(".timePeriodMillis(").append(throttle.getTimePeriodMillis()).append(")");
            }
        } else {
            buffer.append(".").append(processor.getShortName()).append("()");
        }

        List<ProcessorDefinition> outputs = processor.getOutputs();
        for (ProcessorDefinition nextProcessor : outputs) {
            renderProcessor(buffer, nextProcessor);
        }
    }

}
