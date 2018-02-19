/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

def MAVEN_PARAMS = '-B -e -fae -V -Dmaven.repo.local=/home/jenkins/jenkins-slave/maven-repositories/0'

pipeline {

    agent {
        label 'ubuntu'
    }

    tools {
        jdk 'JDK 1.8 (latest)'
    }

    options {
        buildDiscarder(
            logRotator(artifactNumToKeepStr: '5', numToKeepStr: '10')
        )
    }

    stages {

        stage('Build') {
            steps {
                sh "./mvnw $MAVEN_PARAMS -Dnoassembly -Dmaven.test.skip.exec=true -Dmaven.install.skip=true clean install"
            }
        }

        stage('Checks') {
            steps {
                sh "./mvnw $MAVEN_PARAMS -Psourcecheck checkstyle:check"
            }
            post {
                always {
                    checkstyle pattern: '**/checkstyle-result.xml', canRunOnFailed: true
                }
            }
        }

        stage('Test') {
            steps {
                sh "./mvnw $MAVEN_PARAMS -Pintegration -Dnoassembly -Dmaven.test.failure.ignore=true test"
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                    junit '**/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Deploy') {
            steps {
                sh "./mvnw $MAVEN_PARAMS -Pdeploy -Dnoassembly -Dmaven.test.skip.exec=true install"
            }
        }
    }
}

