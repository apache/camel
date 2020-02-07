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
package org.apache.camel.component.aws2.ec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.CreateTagsResponse;
import software.amazon.awssdk.services.ec2.model.DeleteTagsRequest;
import software.amazon.awssdk.services.ec2.model.DeleteTagsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.GroupIdentifier;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceMonitoring;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateChange;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.InstanceStatus;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.MonitorInstancesRequest;
import software.amazon.awssdk.services.ec2.model.MonitorInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Monitoring;
import software.amazon.awssdk.services.ec2.model.MonitoringState;
import software.amazon.awssdk.services.ec2.model.RebootInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RebootInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StartInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesResponse;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.ec2.model.UnmonitorInstancesRequest;
import software.amazon.awssdk.services.ec2.model.UnmonitorInstancesResponse;

public class AmazonEC2ClientMock implements Ec2Client {

    @Override
    public RunInstancesResponse runInstances(RunInstancesRequest runInstancesRequest) {
        RunInstancesResponse.Builder result = RunInstancesResponse.builder();
        if (runInstancesRequest.imageId().equals("test-1")) {
            Collection<Instance> instances = new ArrayList<>();
            Instance.Builder ins = Instance.builder();
            ins.imageId(runInstancesRequest.imageId());
            ins.instanceType(runInstancesRequest.instanceType());
            ins.instanceId("instance-1");
            if (runInstancesRequest.securityGroups() != null) {
                if (runInstancesRequest.securityGroups().contains("secgroup-1") && runInstancesRequest.securityGroups().contains("secgroup-2")) {
                    GroupIdentifier.Builder id1 = GroupIdentifier.builder();
                    id1.groupId("id-1");
                    id1.groupName("secgroup-1");
                    GroupIdentifier.Builder id2 = GroupIdentifier.builder();
                    id2.groupId("id-2");
                    id2.groupName("secgroup-2");
                    Collection<GroupIdentifier> secGroups = new ArrayList<>();
                    secGroups.add(id1.build());
                    secGroups.add(id2.build());
                    ins.securityGroups(secGroups);
                } else if (ObjectHelper.isNotEmpty(runInstancesRequest.keyName())) {
                    if (ObjectHelper.isNotEmpty(runInstancesRequest.keyName().contains("keypair-1"))) {
                        GroupIdentifier.Builder id1 = GroupIdentifier.builder();
                        id1.groupId("id-3");
                        id1.groupName("secgroup-3");
                        GroupIdentifier.Builder id2 = GroupIdentifier.builder();
                        id2.groupId("id-4");
                        id2.groupName("secgroup-4");
                        Collection<GroupIdentifier> secGroups = new ArrayList<>();
                        secGroups.add(id1.build());
                        secGroups.add(id2.build());
                        ins.securityGroups(secGroups);
                    }
                }
            }
            instances.add(ins.build());
            result.instances(instances);
        } else {
            AwsServiceException.Builder builder = AwsServiceException.builder();
            AwsErrorDetails.Builder builderError = AwsErrorDetails.builder();
            builderError.errorMessage("The image-id doesn't exists");
            builder.awsErrorDetails(builderError.build());
            AwsServiceException ase = builder.build();
            throw ase;
        }
        return result.build();

    }

    @Override
    public StartInstancesResponse startInstances(StartInstancesRequest startInstancesRequest) {
        StartInstancesResponse.Builder result = StartInstancesResponse.builder();
        if (startInstancesRequest.instanceIds().get(0).equals("test-1")) {
            Collection<InstanceStateChange> coll = new ArrayList<>();
            InstanceStateChange.Builder sc = InstanceStateChange.builder();
            InstanceState.Builder previousState = InstanceState.builder();
            previousState.code(80);
            previousState.name(InstanceStateName.STOPPED);
            InstanceState.Builder newState = InstanceState.builder();
            newState.code(16);
            newState.name(InstanceStateName.RUNNING);
            sc.previousState(previousState.build());
            sc.currentState(newState.build());
            sc.instanceId("test-1");
            coll.add(sc.build());
            result.startingInstances(coll);
        } else {
            AwsServiceException.Builder builder = AwsServiceException.builder();
            AwsErrorDetails.Builder builderError = AwsErrorDetails.builder();
            builderError.errorMessage("The image-id doesn't exists");
            builder.awsErrorDetails(builderError.build());
            AwsServiceException ase = builder.build();
            throw ase;
        }
        return result.build();
    }

    @Override
    public StopInstancesResponse stopInstances(StopInstancesRequest stopInstancesRequest) {
        StopInstancesResponse.Builder builder = StopInstancesResponse.builder();
        if (stopInstancesRequest.instanceIds().get(0).equals("test-1")) {
            Collection<InstanceStateChange> coll = new ArrayList<>();
            InstanceStateChange.Builder sc = InstanceStateChange.builder();
            InstanceState.Builder previousState = InstanceState.builder();
            previousState.code(80);
            previousState.name(InstanceStateName.RUNNING);
            InstanceState.Builder newState = InstanceState.builder();
            newState.code(16);
            newState.name(InstanceStateName.STOPPED);
            sc.previousState(previousState.build());
            sc.currentState(newState.build());
            sc.instanceId("test-1");
            coll.add(sc.build());
            builder.stoppingInstances(coll);
        } else {
            AwsServiceException.Builder exc = AwsServiceException.builder();
            AwsErrorDetails.Builder builderError = AwsErrorDetails.builder();
            builderError.errorMessage("The image-id doesn't exists");
            exc.awsErrorDetails(builderError.build());
            AwsServiceException ase = exc.build();
            throw ase;
        }
        return builder.build();
    }

    @Override
    public TerminateInstancesResponse terminateInstances(TerminateInstancesRequest terminateInstancesRequest) {
        TerminateInstancesResponse.Builder result = TerminateInstancesResponse.builder();
        if (terminateInstancesRequest.instanceIds().contains("test-1")) {
            Collection<InstanceStateChange> coll = new ArrayList<>();
            InstanceStateChange.Builder sc = InstanceStateChange.builder();
            InstanceState.Builder previousState = InstanceState.builder();
            previousState.code(80);
            previousState.name(InstanceStateName.RUNNING);
            InstanceState.Builder newState = InstanceState.builder();
            newState.code(16);
            newState.name(InstanceStateName.TERMINATED);
            sc.previousState(previousState.build());
            sc.currentState(newState.build());
            sc.instanceId("test-1");
            coll.add(sc.build());
            result.terminatingInstances(coll);
        } else {
            AwsServiceException.Builder exc = AwsServiceException.builder();
            AwsErrorDetails.Builder builderError = AwsErrorDetails.builder();
            builderError.errorMessage("The image-id doesn't exists");
            exc.awsErrorDetails(builderError.build());
            AwsServiceException ase = exc.build();
            throw ase;
        }
        return result.build();
    }

    @Override
    public DescribeInstancesResponse describeInstances(DescribeInstancesRequest describeInstancesRequest) {
        DescribeInstancesResponse.Builder result = DescribeInstancesResponse.builder();
        if (describeInstancesRequest.instanceIds().isEmpty()) {
            Collection<Reservation> list = new ArrayList<>();
            Reservation.Builder res = Reservation.builder();
            res.ownerId("1");
            res.requesterId("user-test");
            res.reservationId("res-1");
            Collection<Instance> instances = new ArrayList<>();
            Instance.Builder ins = Instance.builder();
            ins.imageId("id-1");
            ins.instanceType(InstanceType.T2_MICRO);
            ins.instanceId("instance-1");
            instances.add(ins.build());
            Instance.Builder ins1 = Instance.builder();
            ins1.imageId("id-2");
            ins1.instanceType(InstanceType.T2_MICRO);
            ins1.instanceId("instance-2");
            instances.add(ins1.build());
            res.instances(instances);
            list.add(res.build());
            result.reservations(list);
        } else {
            if (describeInstancesRequest.instanceIds().contains("instance-1")) {
                Collection<Reservation> list = new ArrayList<>();
                Reservation.Builder res = Reservation.builder();
                res.ownerId("1");
                res.requesterId("user-test");
                res.reservationId("res-1");
                Collection<Instance> instances = new ArrayList<>();
                Instance.Builder ins = Instance.builder();
                ins.imageId("id-1");
                ins.instanceType(InstanceType.T2_MICRO);
                ins.instanceId("instance-1");
                instances.add(ins.build());
                res.instances(instances);
                list.add(res.build());
                result.reservations(list);
            }
        }
        return result.build();
    }

    @Override
    public DescribeInstanceStatusResponse describeInstanceStatus(DescribeInstanceStatusRequest describeInstanceStatusRequest) {
        DescribeInstanceStatusResponse.Builder result = DescribeInstanceStatusResponse.builder();
        Collection<InstanceStatus> instanceStatuses = new ArrayList<>();
        if (describeInstanceStatusRequest.instanceIds().isEmpty()) {
            InstanceStatus.Builder status = InstanceStatus.builder();
            status.instanceId("test-1");
            status.instanceState(InstanceState.builder().name(InstanceStateName.RUNNING).build());
            instanceStatuses.add(status.build());
            InstanceStatus.Builder status1 = InstanceStatus.builder();
            status1.instanceId("test-2");
            status1.instanceState(InstanceState.builder().name(InstanceStateName.STOPPED).build());
            instanceStatuses.add(status1.build());
        } else {
            if (describeInstanceStatusRequest.instanceIds().contains("test-1")) {
                InstanceStatus.Builder status = InstanceStatus.builder();
                status.instanceId("test-1");
                status.instanceState(InstanceState.builder().name(InstanceStateName.RUNNING).build());
                instanceStatuses.add(status.build());
            }
            if (describeInstanceStatusRequest.instanceIds().contains("test-2")) {
                InstanceStatus.Builder status1 = InstanceStatus.builder();
                status1.instanceId("test-2");
                status1.instanceState(InstanceState.builder().name(InstanceStateName.STOPPED).build());
                instanceStatuses.add(status1.build());
            }
        }
        result.instanceStatuses(instanceStatuses);
        return result.build();
    }

    @Override
    public RebootInstancesResponse rebootInstances(RebootInstancesRequest rebootInstancesRequest) {
        return RebootInstancesResponse.builder().build();
    }

    @Override
    public MonitorInstancesResponse monitorInstances(MonitorInstancesRequest monitorInstancesRequest) {
        MonitorInstancesResponse.Builder result = MonitorInstancesResponse.builder();
        if (!monitorInstancesRequest.instanceIds().isEmpty()) {
            Collection<InstanceMonitoring> coll = new ArrayList<>();
            Iterator<String> it = monitorInstancesRequest.instanceIds().iterator();
            while (it.hasNext()) {
                String id = it.next();
                InstanceMonitoring.Builder mon = InstanceMonitoring.builder();
                mon.instanceId(id);
                Monitoring.Builder monitoring = Monitoring.builder();
                monitoring.state(MonitoringState.ENABLED);
                mon.monitoring(monitoring.build());
                coll.add(mon.build());
            }
            result.instanceMonitorings(coll);
        }
        return result.build();
    }

    @Override
    public UnmonitorInstancesResponse unmonitorInstances(UnmonitorInstancesRequest unmonitorInstancesRequest) {
        UnmonitorInstancesResponse.Builder result = UnmonitorInstancesResponse.builder();
        if (!unmonitorInstancesRequest.instanceIds().isEmpty()) {
            Collection<InstanceMonitoring> coll = new ArrayList<>();
            Iterator<String> it = unmonitorInstancesRequest.instanceIds().iterator();
            while (it.hasNext()) {
                String id = it.next();
                InstanceMonitoring.Builder mon = InstanceMonitoring.builder();
                mon.instanceId(id);
                Monitoring.Builder monitoring = Monitoring.builder();
                monitoring.state(MonitoringState.DISABLED);
                mon.monitoring(monitoring.build());
                coll.add(mon.build());
            }
            result.instanceMonitorings(coll);
        }
        return result.build();
    }

    @Override
    public CreateTagsResponse createTags(CreateTagsRequest createTagsRequest) {
        return CreateTagsResponse.builder().build();
    }

    @Override
    public DeleteTagsResponse deleteTags(DeleteTagsRequest deleteTagsRequest) {
        return DeleteTagsResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public void close() {
    }
}
