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

# script which prepares all needed certificates and keystore
# properties of certificates has to be confirmed
# password to be filled in the last step has to be 'test'

mkdir cert
mkdir ca

#generate ca certificate and key
openssl req -x509 -config openssl-ca.cnf -newkey rsa:4096 -sha256 -nodes -out ca/cacert.pem -outform PEM

#generate csr
openssl req -config openssl-server.cnf -newkey rsa:2048 -sha256 -nodes -out cert/servercert.csr -outform PEM

#sign
touch ca/index.txt
echo '01' > ca/serial.txt
openssl ca -config openssl-ca.cnf -policy signing_policy -extensions signing_req -out cert/servercert.pem -infiles cert/servercert.csr

#import into keystore
openssl pkcs12 -export -in cert/servercert.pem -inkey cert/serverkey.pem -certfile ca/cacert.pem -name "test" -out keystore.p12 -passout pass:test
keytool -importkeystore -srckeystore keystore.p12 -srcstoretype pkcs12 -destkeystore keystore -deststoretype JKS -storepass testtest
