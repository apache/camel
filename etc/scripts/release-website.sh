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
DOWNLOAD=${2:-/tmp/camel-release}
mkdir ${DOWNLOAD} 2>/dev/null

# The following component contain schema definitions that must be published
RUNDIR=$(cd ${0%/*} && echo $PWD)
COMPLIST=( "camel-spring:spring"
  "camel-cxf:cxf"
  "camel-spring-integration:spring/integration"
  "camel-spring-security:spring-security"
  "camel-blueprint:blueprint" )
SITE_DIR="${DOWNLOAD}/websites/production/camel"
WEBSITE_URL="https://svn.apache.org/repos/infra/websites/production/camel/content"
GIT_WEBSITE_URL="https://gitbox.apache.org/repos/asf/camel-website.git"

if [ -z "${VERSION}" -o ! -d "${DOWNLOAD}" ]
then
 echo "Usage: release-website.sh <camel-version> [temp-directory]"
 exit 1
fi

echo "Using ${SITE_DIR} as temporary checkout area."
if [ -d "${SITE_DIR}" ]
then
 echo "Temporary checkout area should not exist or should be empty."
 echo "Please remove and rerun release script."
 exit 1
fi
mkdir -p "${SITE_DIR}/${VERSION}"

echo "################################################################################"
echo "               DOWNLOADING COMPONENTS FROM APACHE REPOSITORY                    "
echo "################################################################################"
for comp in ${COMPLIST[*]}; do
  src=${comp%:*}
  dest=${comp#*:}
  wget -e robots=off --wait 3 --no-check-certificate \
    -r -np "--reject=html,txt" "--accept=xsd" "--follow-tags=" \
    -P "${DOWNLOAD}/${VERSION}" -nH "--cut-dirs=3" "--level=1" "--ignore-length" \
    "https://repository.apache.org/content/repositories/releases/org/apache/camel/${src}/${VERSION}/"
done
echo

echo "################################################################################"
echo "                           CHECKOUT CAMEL WEBSITE                               "
echo "################################################################################"
cd "${SITE_DIR}/${VERSION}" && git clone "${GIT_WEBSITE_URL}"

echo "################################################################################"
echo "                           PUBLISH CAMEL SCHEMAS                                "
echo "################################################################################"
for comp in ${COMPLIST[*]}; do
  src=${comp%:*}
  dest=${comp#*:}
  cp ${DOWNLOAD}/${VERSION}/org/apache/camel/${src}/${VERSION}/*.xsd ${SITE_DIR}/${VERSION}/camel-website/static/schema/${dest}/
  # update_latest_released_schema("${SITE_DIR}/content/schema/${dest}/")
done
echo

echo "################################################################################"
echo "                  DOWNLOADING MANUALS FROM APACHE REPOSITORY                     "
echo "################################################################################"
wget -e robots=off --wait 3 --no-check-certificate \
 -r -np "--reject=txt" "--accept=html,pdf" "--follow-tags=" \
 -P "${DOWNLOAD}/${VERSION}" -nH "--cut-dirs=3" "--level=1" "--ignore-length" \
 "http://repository.apache.org/content/repositories/releases/org/apache/camel/camel-manual/${VERSION}/"

echo "################################################################################"
echo "                           CHECKOUT MANUAL WEBSITE                             "
echo "################################################################################"
cd "${SITE_DIR}/${VERSION}" && svn co --non-interactive "${WEBSITE_URL}/manual/"

echo "################################################################################"
echo "                           PUBLISH CAMEL MANUAL                                "
echo "################################################################################"
cp ${DOWNLOAD}/${VERSION}/org/apache/camel/camel-manual/${VERSION}/camel-manual-${VERSION}.* ${SITE_DIR}/${VERSION}/manual/
echo

echo "NOTE: Manual steps required! Check the schemas and manual files for new artifacts,"
echo "      add them to the repository as required and commit your changes. This step"
echo "      is intentionally not automated at this point to avoid errors."
echo
echo "cd ${SITE_DIR}/${VERSION}/camel-website/"
echo "git status"
echo "git add <schema-${VERSION}-qualifier>.xsd"
echo "git commit -m \"Add XML schemas for Camel ${VERSION}\""
echo
echo "cd ${SITE_DIR}/${VERSION}/manual/"
echo "svn status"
echo "svn add camel-manual-${VERSION}.html"
echo "svn ci -m \"Uploading released manuals for camel-${VERSION}\""
echo
