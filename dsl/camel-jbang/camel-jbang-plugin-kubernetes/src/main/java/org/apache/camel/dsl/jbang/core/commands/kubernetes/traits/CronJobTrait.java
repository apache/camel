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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Container;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.CronJob;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.ObjectHelper;

public class CronJobTrait extends BaseTrait {

    public CronJobTrait() {
        super("cronjob", 900);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        return traitConfig.getCronjob() != null
                && Optional.ofNullable(traitConfig.getCronjob().getEnabled()).orElse(false)
                && ObjectHelper.isNotEmpty(traitConfig.getCronjob().getSchedule());
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        CronJob cronjobTrait = Optional.ofNullable(traitConfig.getCronjob()).orElseGet(CronJob::new);
        CronJobBuilder cronjobBuilder = new CronJobBuilder()
                .withNewMetadata()
                .withName(context.getName())
                .endMetadata()
                .withNewSpec()
                .endSpec();

        Container containerTrait = Optional.ofNullable(traitConfig.getContainer()).orElseGet(Container::new);
        // sets the image pull secret
        Optional.ofNullable(containerTrait.getImagePullSecrets()).orElseGet(List::of)
                .forEach(sec -> cronjobBuilder.editOrNewSpec()
                        .editOrNewJobTemplate()
                        .editOrNewSpec()
                        .editOrNewTemplate()
                        .editOrNewSpec()
                        .addNewImagePullSecret(sec)
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .endJobTemplate()
                        .endSpec());

        // sets the service account
        if (context.getServiceAccount() != null) {
            cronjobBuilder.editOrNewSpec()
                    .editOrNewJobTemplate()
                    .editOrNewSpec()
                    .editOrNewTemplate()
                    .editOrNewSpec()
                    .withServiceAccountName(context.getServiceAccount())
                    .endSpec()
                    .endTemplate()
                    .endSpec()
                    .endJobTemplate()
                    .endSpec();
        }

        // sets the schedule and restart-policy
        cronjobBuilder.editOrNewSpec()
                // set the timezone
                .withSchedule(traitConfig.getCronjob().getSchedule())
                .editOrNewJobTemplate()
                .editOrNewSpec()
                .editOrNewTemplate()
                .editOrNewSpec()
                // set the restartPolicy
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .endJobTemplate()
                .endSpec();

        // sets the timezone
        if (ObjectHelper.isNotEmpty(cronjobTrait.getTimezone())) {
            cronjobBuilder.editOrNewSpec().withTimeZone(cronjobTrait.getTimezone()).endSpec();
        }
        // sets the ActiveDeadlineSeconds
        if (cronjobTrait.getActiveDeadlineSeconds() != null && cronjobTrait.getActiveDeadlineSeconds() > 0) {
            cronjobBuilder.editOrNewSpec().editOrNewJobTemplate().editOrNewSpec()
                    .withActiveDeadlineSeconds(cronjobTrait.getActiveDeadlineSeconds()).endSpec().endJobTemplate().endSpec();
        }
        // sets the BackoffLimit
        if (cronjobTrait.getBackoffLimit() != null && cronjobTrait.getBackoffLimit() > 0) {
            cronjobBuilder.editOrNewSpec().editOrNewJobTemplate().editOrNewSpec()
                    .withBackoffLimit(cronjobTrait.getBackoffLimit()).endSpec().endJobTemplate().endSpec();
        }
        // sets the StartingDeadlineSeconds
        if (cronjobTrait.getStartingDeadlineSeconds() != null && cronjobTrait.getStartingDeadlineSeconds() > 0) {
            cronjobBuilder.editOrNewSpec().withStartingDeadlineSeconds(cronjobTrait.getStartingDeadlineSeconds()).endSpec();
        }
        context.add(cronjobBuilder);
    }
}
