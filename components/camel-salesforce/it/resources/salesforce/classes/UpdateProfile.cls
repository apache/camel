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
@RestResource(urlMapping='/UpdateProfile/*')
global without sharing class UpdateProfile {

    @HttpPatch
    global static String perform() {
        String username = RestContext.request.params.get('username');

        PermissionSet bulkDelete = [SELECT Id FROM PermissionSet WHERE Name = 'Hard_Delete_Permission_Set' LIMIT 1];
        User user = [SELECT Id FROM USER WHERE Username = :username LIMIT 1];

        List<PermissionSetAssignment> previous = [SELECT Id FROM PermissionSetAssignment WHERE PermissionSetId = :bulkDelete.Id AND AssigneeId = :user.Id];

        if (previous.isEmpty()) {
            PermissionSetAssignment assignment = new PermissionSetAssignment(PermissionSetId = bulkDelete.Id, AssigneeId = user.Id);
            upsert assignment;
        }

        return 'success';
    }

}
