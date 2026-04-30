#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

echo "Creating read-writable files"
for file in $(seq 1 100) ; do
	echo ${RANDOM} > /data/rw/${file}.txt ;
done

echo "Creating read-only files"
for file in $(seq 1 100) ; do
	echo ${RANDOM} > /data/ro/${file}.txt ;
done

echo "Creating no-delete files (root-owned, sticky bit on directory)"
for file in $(seq 1 10) ; do
	echo ${RANDOM} > /data/no-delete/${file}.txt ;
done
chmod 1777 /data/no-delete

echo "Creating unix-security files (simulates ONTAP UNIX security style)"
for file in $(seq 1 10) ; do
	echo ${RANDOM} > /data/unix-security/${file}.txt ;
done
chmod 1777 /data/unix-security

echo "Creating fixed-delete files (same as no-delete but with correct ownership)"
for file in $(seq 1 10) ; do
	echo ${RANDOM} > /data/fixed-delete/${file}.txt ;
done

echo "Creating unix-owned files (UNIX security style + correct ownership)"
for file in $(seq 1 10) ; do
	echo ${RANDOM} > /data/unix-owned/${file}.txt ;
done

useradd camel
printf "camelTester123\ncamelTester123\n" | smbpasswd -s -a camel

chown -Rv camel /data/rw
chown -Rv camel /data/fixed-delete
chown -Rv camel /data/unix-owned

nmbd -D
smbd -D -s /etc/samba/smb.conf

while true ; do
	sleep 10
done