/*
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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.knative;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.fabric8.knative.serving.v1.ServiceBuilder;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.ServiceTrait;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitContext;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.KnativeService;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.ObjectHelper;

public class KnativeServiceTrait extends KnativeBaseTrait {

    // Auto-scaling annotations
    private static final String knativeServingClassAnnotation = "autoscaling.knative.dev/class";
    private static final String knativeServingMetricAnnotation = "autoscaling.knative.dev/metric";
    private static final String knativeServingTargetAnnotation = "autoscaling.knative.dev/target";
    private static final String knativeServingMinScaleAnnotation = "autoscaling.knative.dev/minScale";
    private static final String knativeServingMaxScaleAnnotation = "autoscaling.knative.dev/maxScale";
    // Rollout annotation
    private static final String knativeServingRolloutDurationAnnotation = "serving.knative.dev/rolloutDuration";
    // Visibility label
    private static final String knativeServingVisibilityLabel = "networking.knative.dev/visibility";

    public KnativeServiceTrait() {
        super("knative-service", ServiceTrait.SERVICE_TRAIT_ORDER - 100);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        if (context.getKnativeService().isPresent()) {
            return false;
        }
        // one of Knative traits needs to be explicitly enabled
        boolean enabled = false;
        if (traitConfig.getKnativeService() != null) {
            enabled = Optional.ofNullable(traitConfig.getKnativeService().getEnabled()).orElse(false);
        }
        return enabled && TraitHelper.exposesHttpService(context);
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        KnativeService serviceTrait = Optional.ofNullable(traitConfig.getKnativeService()).orElseGet(KnativeService::new);

        Map<String, String> serviceAnnotations = new HashMap<>();
        // Set Knative rollout
        if (ObjectHelper.isNotEmpty(serviceTrait.getRolloutDuration())) {
            serviceAnnotations.put(knativeServingRolloutDurationAnnotation, serviceTrait.getRolloutDuration());
        }

        if (serviceTrait.getAnnotations() != null) {
            serviceAnnotations.putAll(serviceTrait.getAnnotations());
        }

        Map<String, String> revisionAnnotations = new HashMap<>();
        // Set Knative auto-scaling
        if (serviceTrait.get_class() != null && ObjectHelper.isNotEmpty(serviceTrait.get_class().getValue())) {
            revisionAnnotations.put(knativeServingClassAnnotation, serviceTrait.get_class().getValue());
        }
        if (ObjectHelper.isNotEmpty(serviceTrait.getAutoscalingMetric())) {
            revisionAnnotations.put(knativeServingMetricAnnotation, serviceTrait.getAutoscalingMetric());
        }
        if (serviceTrait.getAutoscalingTarget() != null) {
            revisionAnnotations.put(knativeServingTargetAnnotation, serviceTrait.getAutoscalingTarget().toString());
        }
        if (serviceTrait.getMinScale() != null && serviceTrait.getMinScale() > 0) {
            revisionAnnotations.put(knativeServingMinScaleAnnotation, serviceTrait.getMinScale().toString());
        }
        if (serviceTrait.getMaxScale() != null && serviceTrait.getMaxScale() > 0) {
            revisionAnnotations.put(knativeServingMaxScaleAnnotation, serviceTrait.getMaxScale().toString());
        }

        Map<String, String> serviceLabels = new HashMap<>();
        serviceLabels.put(BaseTrait.KUBERNETES_LABEL_NAME, context.getName());

        // Make sure the Eventing webhook will select the source resource, in order to inject the sink information.
        // This is necessary for Knative environments, that are configured with SINK_BINDING_SELECTION_MODE=inclusion.
        // - https://knative.dev/v1.3-docs/eventing/custom-event-source/sinkbinding/create-a-sinkbinding/#optional-choose-sinkbinding-namespace-selection-behavior
        // - https://github.com/knative/operator/blob/release-1.2/docs/configuration.md#specsinkbindingselectionmode
        serviceLabels.put("bindings.knative.dev/include", "true");

        if (serviceTrait.getVisibility() != null && ObjectHelper.isNotEmpty(serviceTrait.getVisibility().getValue())) {
            serviceLabels.put(knativeServingVisibilityLabel, serviceTrait.getVisibility().getValue());
        }

        ServiceBuilder service = new ServiceBuilder()
                .withNewMetadata()
                .withName(context.getName())
                .addToLabels(serviceLabels)
                .addToAnnotations(serviceAnnotations)
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(BaseTrait.KUBERNETES_LABEL_NAME, context.getName())
                .addToAnnotations(revisionAnnotations)
                .endMetadata()
                .endTemplate()
                .endSpec();

        if (serviceTrait.getTimeoutSeconds() != null && serviceTrait.getTimeoutSeconds() > 0) {
            service.editSpec()
                    .editTemplate()
                    .editSpec()
                    .withTimeoutSeconds(serviceTrait.getTimeoutSeconds())
                    .endSpec()
                    .endTemplate()
                    .endSpec();
        }

        if (context.getServiceAccount() != null) {
            service.editSpec()
                    .editTemplate()
                    .editSpec()
                    .withServiceAccountName(context.getServiceAccount())
                    .endSpec()
                    .endTemplate()
                    .endSpec();
        }

        context.add(service);
    }

}
