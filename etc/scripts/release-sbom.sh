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
#

VERSION=${1}
DOWNLOAD=${2:-/tmp/camel-sbom}
mkdir -p ${DOWNLOAD} 2>/dev/null

# The following component contain schema definitions that must be published
RUNDIR=$(cd ${0%/*} && echo $PWD)
DIST_REPO="https://dist.apache.org/repos/dist/release/camel/camel/"

if [ -z "${VERSION}" -o ! -d "${DOWNLOAD}" ]
then
 echo "Usage: release-sbom.sh <camel-version> [temp-directory]"
 exit 1
fi

echo "################################################################################"
echo "                  DOWNLOADING SBOMs FROM APACHE REPOSITORY                     "
echo "################################################################################"
echo "${DOWNLOAD}/${VERSION}"

wget -e robots=off --wait 3 --no-check-certificate \
 -r -np "--reject=html,txt" "--follow-tags=" \
 -P "${DOWNLOAD}/${VERSION}" -nH "--cut-dirs=3" "--level=1" "--ignore-length" \
 "https://repository.apache.org/content/repositories/releases/org/apache/camel/camel/${VERSION}/camel-${VERSION}-cyclonedx.xml"
 
 wget -e robots=off --wait 3 --no-check-certificate \
 -r -np "--reject=html,txt" "--follow-tags=" \
 -P "${DOWNLOAD}/${VERSION}" -nH "--cut-dirs=3" "--level=1" "--ignore-length" \
 "https://repository.apache.org/content/repositories/releases/org/apache/camel/camel/${VERSION}/camel-${VERSION}-cyclonedx.json"

DOWNLOAD_LOCATION="${DOWNLOAD}/${VERSION}/org/apache/camel/camel/${VERSION}"

mv ${DOWNLOAD_LOCATION}/camel-${VERSION}-cyclonedx.json ${DOWNLOAD_LOCATION}/apache-camel-${VERSION}-sbom.json
mv ${DOWNLOAD_LOCATION}/camel-${VERSION}-cyclonedx.xml ${DOWNLOAD_LOCATION}/apache-camel-${VERSION}-sbom.xml
./sign.sh ${DOWNLOAD_LOCATION}/

svn import ${DOWNLOAD_LOCATION}/apache-camel-${VERSION}-sbom.json https://dist.apache.org/repos/dist/release/camel/apache-camel/${VERSION}/apache-camel-${VERSION}-sbom.json -m "Import Camel SBOMs JSON release"
svn import ${DOWNLOAD_LOCATION}/apache-camel-${VERSION}-sbom.json.asc https://dist.apache.org/repos/dist/release/camel/apache-camel/${VERSION}/apache-camel-${VERSION}-sbom.json.asc -m "Import Camel SBOMs JSON release"
svn import ${DOWNLOAD_LOCATION}/apache-camel-${VERSION}-sbom.json.sha512 https://dist.apache.org/repos/dist/release/camel/apache-camel/${VERSION}/apache-camel-${VERSION}-sbom.json.sha512 -m "Import Camel SBOMs JSON release"
svn import ${DOWNLOAD_LOCATION}/apache-camel-${VERSION}-sbom.xml https://dist.apache.org/repos/dist/release/camel/apache-camel/${VERSION}/apache-camel-${VERSION}-sbom.xml -m "Import Camel SBOMs XML release"
svn import ${DOWNLOAD_LOCATION}/apache-camel-${VERSION}-sbom.xml.asc https://dist.apache.org/repos/dist/release/camel/apache-camel/${VERSION}/apache-camel-${VERSION}-sbom.xml.asc -m "Import Camel SBOMs XML release"
svn import ${DOWNLOAD_LOCATION}/apache-camel-${VERSION}-sbom.xml.sha512 https://dist.apache.org/repos/dist/release/camel/apache-camel/${VERSION}/apache-camel-${VERSION}-sbom.xml.sha512 -m "Import Camel SBOMs XML release"
echo "SBOM uploaded in dist/release"

rm -rf ${DOWNLOAD_LOCATION}/
echo "Removed Temporary directory"

