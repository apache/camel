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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.KeyToPathBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Mount;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

public class MountTrait extends BaseTrait {

    private static final Pattern RESOURCE_VALUE_EXPRESSION
            = Pattern.compile("^([\\w.\\-_:]+)(/([\\w.\\-_:]+))?(@([\\w.\\-_:/]+))?$");

    public static final int MOUNT_TRAIT_ORDER = ContainerTrait.CONTAINER_TRAIT_ORDER + 10;

    public MountTrait() {
        super("mount", MOUNT_TRAIT_ORDER);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        Mount mountTrait = Optional.ofNullable(traitConfig.getMount()).orElseGet(Mount::new);

        if (mountTrait.getConfigs() != null) {
            for (String config : mountTrait.getConfigs()) {
                if (!config.startsWith("configmap:") && !config.startsWith("secret:")) {
                    throw new RuntimeCamelException(
                            "Unsupported config %s, must be a configmap or secret resource".formatted(config));
                }
            }
        }

        if (mountTrait.getResources() != null) {
            for (String resource : mountTrait.getResources()) {
                if (!resource.startsWith("configmap:") && !resource.startsWith("secret:")) {
                    throw new RuntimeCamelException(
                            "Unsupported resource %s, must be a configmap or secret resource".formatted(resource));
                }
            }
        }

        return true;
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Mount mountTrait = Optional.ofNullable(traitConfig.getMount()).orElseGet(Mount::new);

        List<Volume> volumes = new ArrayList<>();
        List<VolumeMount> volumeMounts = new ArrayList<>();

        configureVolumesAndMounts(mountTrait, volumes, volumeMounts);

        // Deployment
        Optional<DeploymentBuilder> deployment = context.getDeployment();
        deployment.ifPresent(d -> d.editOrNewSpec()
                .editOrNewTemplate()
                .editOrNewSpec()
                .addAllToVolumes(volumes)
                .editFirstContainer()
                .addAllToVolumeMounts(volumeMounts)
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec());
    }

    private void configureVolumesAndMounts(Mount mountTrait, List<Volume> volumes, List<VolumeMount> volumeMounts) {
        if (mountTrait.getConfigs() != null) {
            for (String c : mountTrait.getConfigs()) {
                mountResource(volumes, volumeMounts, parseConfig(c, MountResource.ContentType.TEXT));
            }
        }

        if (mountTrait.getResources() != null) {
            for (String r : mountTrait.getResources()) {
                mountResource(volumes, volumeMounts, parseConfig(r, MountResource.ContentType.DATA));
            }
        }

        if (mountTrait.getVolumes() != null) {
            for (String v : mountTrait.getVolumes()) {
                mountResource(volumes, volumeMounts, parseConfig(v, null));
            }
        }
    }

    private void mountResource(List<Volume> volumes, List<VolumeMount> volumeMounts, MountResource mountResource) {
        String volumeName = KubernetesHelper.sanitize(mountResource.name);
        String dstDir = Optional.ofNullable(mountResource.destinationPath).orElse("");
        String dstFile;
        if (!dstDir.isEmpty() && !mountResource.key.isEmpty()) {
            dstFile = FileUtil.onlyName(dstDir);
        } else {
            dstFile = mountResource.key;
        }

        Volume vol = getVolume(volumeName, mountResource, dstFile);
        boolean readOnly = mountResource.storageType != MountResource.StorageType.PVC;
        VolumeMount mnt = getMount(volumeName, getMountPath(mountResource, dstDir), dstFile, readOnly);

        volumes.add(vol);
        volumeMounts.add(mnt);
    }

    private VolumeMount getMount(String volumeName, String mountPath, String subPath, boolean readOnly) {
        VolumeMountBuilder mount = new VolumeMountBuilder()
                .withName(volumeName)
                .withMountPath(mountPath)
                .withReadOnly(readOnly);

        if (ObjectHelper.isNotEmpty(subPath)) {
            mount.withSubPath(subPath);
        }

        return mount.build();
    }

    private Volume getVolume(String volumeName, MountResource mountResource, String mountPath) {
        VolumeBuilder volume = new VolumeBuilder()
                .withName(volumeName);

        switch (mountResource.storageType) {
            case CONFIGMAP:
                ConfigMapVolumeSourceBuilder cmVolumeSource = new ConfigMapVolumeSourceBuilder()
                        .withName(mountResource.name);

                if (ObjectHelper.isNotEmpty(mountResource.key)) {
                    cmVolumeSource.addToItems(new KeyToPathBuilder()
                            .withKey(mountResource.key)
                            .withPath(Optional.ofNullable(mountPath).orElse(mountResource.key))
                            .build());
                }

                volume.withConfigMap(cmVolumeSource.build()).build();
                break;
            case SECRET:
                SecretVolumeSourceBuilder volumeSource = new SecretVolumeSourceBuilder()
                        .withSecretName(mountResource.name);

                if (ObjectHelper.isNotEmpty(mountResource.key)) {
                    volumeSource.addToItems(new KeyToPathBuilder()
                            .withKey(mountResource.key)
                            .withPath(Optional.ofNullable(mountPath).orElse(mountResource.key))
                            .build());
                }

                volume.withSecret(volumeSource.build()).build();
                break;
            case PVC:
                volume.withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(mountResource.name)
                        .build());
                break;
        }

        return volume.build();
    }

    private String getMountPath(MountResource mountResource, String mountPoint) {
        if (!mountPoint.isEmpty()) {
            return mountPoint;
        }

        String baseMountPoint;
        if (mountResource.contentType == MountResource.ContentType.DATA) {
            baseMountPoint = "/etc/camel/resources.d";
        } else {
            baseMountPoint = "/etc/camel/conf.d";
        }

        if (mountResource.storageType == MountResource.StorageType.SECRET) {
            baseMountPoint += "/_secrets";
        } else {
            baseMountPoint += "/_configmaps";
        }

        return Path.of(baseMountPoint, mountResource.name).toString();
    }

    private MountResource parseConfig(String expression, MountResource.ContentType contentType) {
        if (expression.startsWith("configmap:")) {
            return createConfig(expression.substring("configmap:".length()), MountResource.StorageType.CONFIGMAP, contentType);
        } else if (expression.startsWith("secret:")) {
            return createConfig(expression.substring("secret:".length()), MountResource.StorageType.SECRET, contentType);
        } else { // volumes
            String[] configParts = expression.split(":", 2);

            if (configParts.length != 2) {
                throw new RuntimeCamelException(
                        "Unsupported volume configuration '%s', expected format is \"<resourceName>:<destinationPath>\""
                                .formatted(expression));
            }

            return new MountResource(MountResource.StorageType.PVC, null, configParts[0], "", configParts[1]);
        }
    }

    private MountResource createConfig(
            String expression, MountResource.StorageType storageType, MountResource.ContentType contentType) {
        Matcher resourceCoordinates = RESOURCE_VALUE_EXPRESSION.matcher(expression);
        if (resourceCoordinates.matches()) {
            return new MountResource(
                    storageType, contentType, resourceCoordinates.group(0), resourceCoordinates.group(2),
                    resourceCoordinates.group(4));
        } else {
            return new MountResource(storageType, contentType, expression, "", "");
        }
    }

    private record MountResource(StorageType storageType, ContentType contentType, String name, String key,
            String destinationPath) {
        private enum ContentType {
            DATA,
            TEXT
        }

        private enum StorageType {
            CONFIGMAP,
            SECRET,
            PVC
        }
    }

}
