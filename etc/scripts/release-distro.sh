#!/usr/bin/env bash

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

VERSION=${1}
DOWNLOAD=${2:-/tmp/camel-release}
mkdir ${DOWNLOAD} 2>/dev/null

# The following component contain schema definitions that must be published
RUNDIR=$(cd ${0%/*} && echo $PWD)
COMPLIST=( "camel-spring:spring"
  "camel-cxf:cxf"
  "camel-osgi:osgi"
  "camel-spring-integration:spring/integration"
  "camel-spring-security:spring-security"
  "camel-blueprint:blueprint" )
DIST_DIR="/www/www.apache.org/dist"
SITE_DIR="/www/camel.apache.org"

if [ ! -d "${DIST_DIR}/camel/apache-camel" ]
then
 echo "Apache Camel distro repository not present on this box"
 echo "Use this script on people.apache.org to publish release"
 exit 1
fi

if [ -z "${VERSION}" -o ! -d "${DOWNLOAD}" ]
then
 echo "Usage: publish_camel-distro.sh <camel-version> [temp-directory]"
 exit 1
fi

echo "################################################################################"
echo "                  DOWNLOADING DISTRO FROM APACHE REPOSITORY                     "
echo "################################################################################"
echo "${DOWNLOAD}/${VERSION}"

wget -e robots=off --wait 3 --no-check-certificate \
 -r -np "--reject=html,txt" "--follow-tags=" \
 -P "${DOWNLOAD}/${VERSION}" -nH "--cut-dirs=3" "--level=1" "--ignore-length" \
 "http://repository.apache.org/content/repositories/releases/org/apache/camel/apache-camel/${VERSION}/"

echo "################################################################################"
echo "                         RESET GROUP PERMISSIONS                                "
echo "################################################################################"
# Make sure to give appropriate permissions to the camel group
chown -R :camel ${DOWNLOAD}/${VERSION}
chmod -R g+w ${DOWNLOAD}/${VERSION}
echo

echo "################################################################################"
echo "               MOVE DISTRO TO OFFICIAL APACHE MIRROR REPO                       "
echo "################################################################################"
# Move distro to the correct location
mv "${DOWNLOAD}/${VERSION}/org/apache/camel/apache-camel/${VERSION}" "${DIST_DIR}/camel/apache-camel/"
echo

echo "DONE"
