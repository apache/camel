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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

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
            Collection<Instance> instances = new ArrayList();
            Instance ins = new Instance();
            ins.setImageId(runInstancesRequest.getImageId());
            ins.setInstanceType(runInstancesRequest.getInstanceType());
            ins.setInstanceId("instance-1");
            if (runInstancesRequest.getSecurityGroups().contains("secgroup-1") && runInstancesRequest.getSecurityGroups().contains("secgroup-2")) {
                GroupIdentifier id1 = new GroupIdentifier();
                id1.setGroupId("id-1");
                id1.setGroupName("secgroup-1");
                GroupIdentifier id2 = new GroupIdentifier();
                id2.setGroupId("id-2");
                id2.setGroupName("secgroup-2");
                Collection secGroups = new ArrayList<GroupIdentifier>();
                secGroups.add(id1);
                secGroups.add(id2);
                ins.setSecurityGroups(secGroups);
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
            Collection<Instance> instances = new ArrayList();
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
                Collection<Instance> instances = new ArrayList();
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
}