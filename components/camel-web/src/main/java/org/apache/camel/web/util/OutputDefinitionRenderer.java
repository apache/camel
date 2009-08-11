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

import org.apache.camel.model.AOPDefinition;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.ChoiceDefinition;
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
import org.apache.camel.model.WhenDefinition;

/**
 * 
 */
public class OutputDefinitionRenderer {

    public static void render(StringBuilder buffer, ProcessorDefinition processor) {
        OutputDefinition out = (OutputDefinition)processor;

        boolean notGlobal = buffer.toString().endsWith(")");
        if (notGlobal) {
            buffer.append(".");
        }
        buffer.append(out.getShortName());

        if (out instanceof AOPDefinition) {
            buffer.append("()");
            AOPDefinition aop = (AOPDefinition)out;

            if (aop.getBeforeUri() != null && aop.getAfterUri() != null) {
                buffer.append(".around(\"").append(aop.getBeforeUri());
                buffer.append("\", \"").append(aop.getAfterUri()).append("\")");
            } else if (aop.getBeforeUri() != null) {
                buffer.append(".before(\"").append(aop.getBeforeUri()).append("\")");
            } else if (aop.getAfterUri() != null) {
                buffer.append(".after(\"").append(aop.getAfterUri()).append("\")");
            } else if (aop.getAfterFinallyUri() != null) {
                buffer.append(".afterFinally(\"").append(aop.getAfterUri()).append("\")");
            }

        } else if (out instanceof BeanDefinition) {

        } else if (out instanceof EnrichDefinition) {
            String enrich = out.toString();
            String resourceUri = enrich.substring(enrich.indexOf('[') + 1, enrich.indexOf(' '));
            buffer.append("(\"").append(resourceUri).append("\")");
        } else if (out instanceof FinallyDefinition) {

        } else if (out instanceof InterceptDefinition) {
            buffer.append("()");
            if (out instanceof InterceptFromDefinition) {

            } else {

            }
        } else if (out instanceof InterceptSendToEndpointDefinition) {
            InterceptSendToEndpointDefinition interceptSend = (InterceptSendToEndpointDefinition)out;
            buffer.append("(\"").append(interceptSend.getUri()).append("\")");
            if (interceptSend.getSkipSendToOriginalEndpoint()) {
                buffer.append(".skipSendToOriginalEndpoint()");
            }
        } else if (out instanceof MarshalDefinition) {
            DataFormatDefinition dataFormat = ((MarshalDefinition)out).getDataFormatType();
            buffer.append("().").append(dataFormat.getClass().getAnnotation(XmlRootElement.class).name()).append("()");
        } else if (out instanceof MulticastDefinition) {

        } else if (out instanceof OtherwiseDefinition) {
            buffer.append("()");
        } else if (out instanceof PipelineDefinition) {

        } else if (out instanceof PolicyDefinition) {

        } else if (out instanceof PollEnrichDefinition) {

        } else if (out instanceof ProcessDefinition) {

        } else if (out instanceof RemoveHeaderDefinition) {
            RemoveHeaderDefinition remove = (RemoveHeaderDefinition)out;
            buffer.append("(\"").append(remove.getHeaderName()).append("\")");
        } else if (out instanceof RemovePropertyDefinition) {
            RemovePropertyDefinition remove = (RemovePropertyDefinition)out;
            buffer.append("(\"").append(remove.getPropertyName()).append("\")");
        } else if (out instanceof SetExchangePatternDefinition) {

        } else if (out instanceof SortDefinition) {
            SortDefinition sort = (SortDefinition)out;
            buffer.append("(");
            ExpressionRenderer.renderExpression(buffer, sort.getExpression().toString());
            buffer.append(")");
        } else if (out instanceof StopDefinition) {
            buffer.append("()");
        } else if (out instanceof ThreadsDefinition) {

        } else if (out instanceof TransactedDefinition) {

        } else if (out instanceof TryDefinition) {

        } else if (out instanceof UnmarshalDefinition) {
            DataFormatDefinition dataFormat = ((UnmarshalDefinition)out).getDataFormatType();
            buffer.append("().").append(dataFormat.getClass().getAnnotation(XmlRootElement.class).name()).append("()");
        }
    }
}
