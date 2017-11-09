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
package org.apache.camel.component.aws.ec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateTagsResult;
import com.amazonaws.services.ec2.model.DeleteTagsRequest;
import com.amazonaws.services.ec2.model.DeleteTagsResult;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceMonitoring;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.MonitorInstancesRequest;
import com.amazonaws.services.ec2.model.MonitorInstancesResult;
import com.amazonaws.services.ec2.model.Monitoring;
import com.amazonaws.services.ec2.model.MonitoringState;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.RebootInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.UnmonitorInstancesRequest;
import com.amazonaws.services.ec2.model.UnmonitorInstancesResult;

import org.apache.camel.util.ObjectHelper;

public class AmazonEC2ClientMock extends AmazonEC2Client {

    public AmazonEC2ClientMock() {
        super(new BasicAWSCredentials("user", "secret"));
    }
    
    @Override
    public RunInstancesResult runInstances(RunInstancesRequest runInstancesRequest) {
        RunInstancesResult result = new RunInstancesResult();
        if (runInstancesRequest.getImageId().equals("test-1")) {
            Reservation res = new Reservation();
            res.setOwnerId("1");
            res.setRequesterId("user-test");
            res.setReservationId("res-1");
            Collection<Instance> instances = new ArrayList<>();
            Instance ins = new Instance();
            ins.setImageId(runInstancesRequest.getImageId());
            ins.setInstanceType(runInstancesRequest.getInstanceType());
            ins.setInstanceId("instance-1");
            if (runInstancesRequest.getSecurityGroups() != null) {
                if (runInstancesRequest.getSecurityGroups().contains("secgroup-1") && runInstancesRequest.getSecurityGroups().contains("secgroup-2")) {
                    GroupIdentifier id1 = new GroupIdentifier();
                    id1.setGroupId("id-1");
                    id1.setGroupName("secgroup-1");
                    GroupIdentifier id2 = new GroupIdentifier();
                    id2.setGroupId("id-2");
                    id2.setGroupName("secgroup-2");
                    Collection<GroupIdentifier> secGroups = new ArrayList<>();
                    secGroups.add(id1);
                    secGroups.add(id2);
                    ins.setSecurityGroups(secGroups);
                } else if (ObjectHelper.isNotEmpty(runInstancesRequest.getKeyName())) {
                    if (ObjectHelper.isNotEmpty(runInstancesRequest.getKeyName().contains("keypair-1"))) {
                        GroupIdentifier id1 = new GroupIdentifier();
                        id1.setGroupId("id-3");
                        id1.setGroupName("secgroup-3");
                        GroupIdentifier id2 = new GroupIdentifier();
                        id2.setGroupId("id-4");
                        id2.setGroupName("secgroup-4");
                        Collection<GroupIdentifier> secGroups = new ArrayList<>();
                        secGroups.add(id1);
                        secGroups.add(id2);
                        ins.setSecurityGroups(secGroups);
                    }
                }
            }
            instances.add(ins);
            res.setInstances(instances);
            result.setReservation(res); 
        } else {
            throw new AmazonServiceException("The image-id doesn't exists");
        }
        return result;
        
    }
    
    @Override
    public StartInstancesResult startInstances(StartInstancesRequest startInstancesRequest) {
        StartInstancesResult result = new StartInstancesResult();
        if (startInstancesRequest.getInstanceIds().get(0).equals("test-1")) {
            Collection<InstanceStateChange> coll = new ArrayList<InstanceStateChange>();
            InstanceStateChange sc = new InstanceStateChange();
            InstanceState previousState = new InstanceState();
            previousState.setCode(80);
            previousState.setName(InstanceStateName.Stopped);
            InstanceState newState = new InstanceState();
            newState.setCode(16);
            newState.setName(InstanceStateName.Running);
            sc.setPreviousState(previousState);
            sc.setCurrentState(newState);
            sc.setInstanceId("test-1");
            coll.add(sc);
            result.setStartingInstances(coll);
        } else {
            throw new AmazonServiceException("The image-id doesn't exists");
        }
        return result;       
    }
    
    @Override
    public StopInstancesResult stopInstances(StopInstancesRequest stopInstancesRequest) {
        StopInstancesResult result = new StopInstancesResult();
        if (stopInstancesRequest.getInstanceIds().get(0).equals("test-1")) {
            Collection<InstanceStateChange> coll = new ArrayList<InstanceStateChange>();
            InstanceStateChange sc = new InstanceStateChange();
            InstanceState previousState = new InstanceState();
            previousState.setCode(80);
            previousState.setName(InstanceStateName.Running);
            InstanceState newState = new InstanceState();
            newState.setCode(16);
            newState.setName(InstanceStateName.Stopped);
            sc.setPreviousState(previousState);
            sc.setCurrentState(newState);
            sc.setInstanceId("test-1");
            coll.add(sc);
            result.setStoppingInstances(coll);
        } else {
            throw new AmazonServiceException("The image-id doesn't exists");
        }
        return result;        
    }

    @Override
    public TerminateInstancesResult terminateInstances(TerminateInstancesRequest terminateInstancesRequest) {
        TerminateInstancesResult result = new TerminateInstancesResult();
        if (terminateInstancesRequest.getInstanceIds().contains("test-1")) {
            Collection<InstanceStateChange> coll = new ArrayList<InstanceStateChange>();
            InstanceStateChange sc = new InstanceStateChange();
            InstanceState previousState = new InstanceState();
            previousState.setCode(80);
            previousState.setName(InstanceStateName.Running);
            InstanceState newState = new InstanceState();
            newState.setCode(16);
            newState.setName(InstanceStateName.Terminated);
            sc.setPreviousState(previousState);
            sc.setCurrentState(newState);
            sc.setInstanceId("test-1");
            coll.add(sc);
            result.setTerminatingInstances(coll);
        } else {
            throw new AmazonServiceException("The image-id doesn't exists");
        }
        return result;    
    }
    
    @Override
    public DescribeInstancesResult describeInstances(DescribeInstancesRequest describeInstancesRequest) {
        DescribeInstancesResult result = new DescribeInstancesResult();
        if (describeInstancesRequest.getInstanceIds().isEmpty()) {
            Collection<Reservation> list = new ArrayList<Reservation>();
            Reservation res = new Reservation();
            res.setOwnerId("1");
            res.setRequesterId("user-test");
            res.setReservationId("res-1");
            Collection<Instance> instances = new ArrayList<>();
            Instance ins = new Instance();
            ins.setImageId("id-1");
            ins.setInstanceType(InstanceType.T2Micro);
            ins.setInstanceId("instance-1");
            instances.add(ins);
            Instance ins1 = new Instance();
            ins1.setImageId("id-2");
            ins1.setInstanceType(InstanceType.T2Micro);
            ins1.setInstanceId("instance-2");
            instances.add(ins1);
            res.setInstances(instances);
            list.add(res);
            result.setReservations(list); 
        } else {
            if (describeInstancesRequest.getInstanceIds().contains("instance-1")) {
                Collection<Reservation> list = new ArrayList<Reservation>();
                Reservation res = new Reservation();
                res.setOwnerId("1");
                res.setRequesterId("user-test");
                res.setReservationId("res-1");
                Collection<Instance> instances = new ArrayList<>();
                Instance ins = new Instance();
                ins.setImageId("id-1");
                ins.setInstanceType(InstanceType.T2Micro);
                ins.setInstanceId("instance-1");
                instances.add(ins);
                res.setInstances(instances);
                list.add(res);
                result.setReservations(list); 
            }
        }
        return result;
    }
    
    @Override
    public DescribeInstanceStatusResult describeInstanceStatus(DescribeInstanceStatusRequest describeInstanceStatusRequest) {
        DescribeInstanceStatusResult result = new DescribeInstanceStatusResult();
        Collection<InstanceStatus> instanceStatuses = new ArrayList<>();
        if (describeInstanceStatusRequest.getInstanceIds().isEmpty()) {
            InstanceStatus status = new InstanceStatus();
            status.setInstanceId("test-1");
            status.setInstanceState(new InstanceState().withName(InstanceStateName.Running));
            instanceStatuses.add(status);
            status.setInstanceId("test-2");
            status.setInstanceState(new InstanceState().withName(InstanceStateName.Stopped));
            instanceStatuses.add(status);
        } else {
            if (describeInstanceStatusRequest.getInstanceIds().contains("test-1")) {
                InstanceStatus status = new InstanceStatus();
                status.setInstanceId("test-1");
                status.setInstanceState(new InstanceState().withName(InstanceStateName.Running));
                instanceStatuses.add(status);
            }
            if (describeInstanceStatusRequest.getInstanceIds().contains("test-2")) {
                InstanceStatus status = new InstanceStatus();
                status.setInstanceId("test-2");
                status.setInstanceState(new InstanceState().withName(InstanceStateName.Stopped));
                instanceStatuses.add(status);
            }
        }
        result.setInstanceStatuses(instanceStatuses);
        return result;
    }

    @Override
    public RebootInstancesResult rebootInstances(RebootInstancesRequest rebootInstancesRequest) {
        return new RebootInstancesResult();
    }
    
    @Override
    public MonitorInstancesResult monitorInstances(MonitorInstancesRequest monitorInstancesRequest) {
        MonitorInstancesResult result = new MonitorInstancesResult();
        if (!monitorInstancesRequest.getInstanceIds().isEmpty()) {
            Collection<InstanceMonitoring> coll = new ArrayList<>();
            Iterator<String> it = monitorInstancesRequest.getInstanceIds().iterator();
            while (it.hasNext()) {
                String id = (String) it.next();
                InstanceMonitoring mon = new InstanceMonitoring();
                mon.setInstanceId(id);
                Monitoring monitoring = new Monitoring();
                monitoring.setState(MonitoringState.Enabled);
                mon.setMonitoring(monitoring); 
                coll.add(mon);
            }
            result.setInstanceMonitorings(coll);
        }
        return result;
    }
    
    @Override
    public UnmonitorInstancesResult unmonitorInstances(UnmonitorInstancesRequest unmonitorInstancesRequest) {
        UnmonitorInstancesResult result = new UnmonitorInstancesResult();
        if (!unmonitorInstancesRequest.getInstanceIds().isEmpty()) {
            Collection<InstanceMonitoring> coll = new ArrayList<>();
            Iterator<String> it = unmonitorInstancesRequest.getInstanceIds().iterator();
            while (it.hasNext()) {
                String id = (String) it.next();
                InstanceMonitoring mon = new InstanceMonitoring();
                mon.setInstanceId(id);
                Monitoring monitoring = new Monitoring();
                monitoring.setState(MonitoringState.Disabled);
                mon.setMonitoring(monitoring); 
                coll.add(mon);
            }
            result.setInstanceMonitorings(coll);
        }
        return result;
    }
    
    @Override
    public CreateTagsResult createTags(CreateTagsRequest createTagsRequest) {
        return new CreateTagsResult();
    }
    
    @Override
    public DeleteTagsResult deleteTags(DeleteTagsRequest deleteTagsRequest) {
        return new DeleteTagsResult();
    }
}