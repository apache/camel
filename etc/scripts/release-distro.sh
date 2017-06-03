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
mkdir -p ${DOWNLOAD} 2>/dev/null

# The following component contain schema definitions that must be published
RUNDIR=$(cd ${0%/*} && echo $PWD)
COMPLIST=( "camel-spring:spring"
  "camel-cxf:cxf"
  "camel-osgi:osgi"
  "camel-spring-integration:spring/integration"
  "camel-spring-security:spring-security"
  "camel-blueprint:blueprint" )
DIST_REPO="https://dist.apache.org/repos/dist/release/camel/apache-camel/"
SITE_DIR="/www/camel.apache.org"


if [ -z "${VERSION}" -o ! -d "${DOWNLOAD}" ]
then
 echo "Usage: release-distro.sh <camel-version> [temp-directory]"
 exit 1
fi

echo "################################################################################"
echo "                  DOWNLOADING DISTRO FROM APACHE REPOSITORY                     "
echo "################################################################################"
echo "${DOWNLOAD}/${VERSION}"

wget -e robots=off --wait 3 --no-check-certificate \
 -r -np "--reject=html,txt" "--follow-tags=" \
 -P "${DOWNLOAD}/${VERSION}" -nH "--cut-dirs=3" "--level=1" "--ignore-length" \
 "https://repository.apache.org/content/repositories/releases/org/apache/camel/apache-camel/${VERSION}/"
# Remove the signature check sum files
rm ${DOWNLOAD}/${VERSION}/org/apache/camel/apache-camel/${VERSION}/*.asc.md5
rm ${DOWNLOAD}/${VERSION}/org/apache/camel/apache-camel/${VERSION}/*.asc.sha1

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
mkdir -p ${DOWNLOAD}/dist 2>/dev/null
svn mkdir ${DIST_REPO}/${VERSION} -m "Apache Camel ${VERSION} release distro placeholder."
cd ${DOWNLOAD}/dist; svn co ${DIST_REPO}/${VERSION}
cp ${DOWNLOAD}/${VERSION}/org/apache/camel/apache-camel/${VERSION}/* ${DOWNLOAD}/dist/${VERSION}/
cd "${DOWNLOAD}/dist/${VERSION}/"; svn add *
echo

echo "Distro artifacts prepared for upload, but not yet uploaded. Verify distro then complete upload!"
echo "cd ${DOWNLOAD}/dist/${VERSION}/"
echo "svn status"
echo "svn ci -m \"Apache Camel ${VERSION} released artifacts.\""
echo
echo "Remove previous distro on same branch if necessary"
echo "DONE"
