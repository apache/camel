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

# This has a JDK and Maven we can use.
FROM registry.access.redhat.com/ubi9/openjdk-11
USER root

#
# the builder script will run this container under as your uid:guid so that the files it creates
# in the host env are owned by you, lets make sure there is uid:guid to match in the container.
RUN for i in $(seq 50 184); do echo "default$i:x:$i:$i:default:/home/default:/bin/bash" >> /etc/passwd; done; 
RUN for i in $(seq 186 2000); do echo "default$i:x:$i:$i:default:/home/default:/bin/bash" >> /etc/passwd; done

CMD ["/bin/bash"]
WORKDIR /src