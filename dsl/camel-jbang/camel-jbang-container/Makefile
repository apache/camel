# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Use bash explicitly in this Makefile to avoid unexpected platform
# incompatibilities among Linux distros.
#
SHELL := /bin/bash

JBANG_VERSION = 0.141.0
CAMEL_VERSION ?=

ifndef CAMEL_VERSION
$(error CAMEL_VERSION is required: make CAMEL_VERSION=4.21.0 ...)
endif

# Use any other for staging or test purposes
IMAGE_NAME ?= docker.io/apache/camel-jbang
BASE_IMAGE_NAME := eclipse-temurin
BASE_IMAGE_VERSION := 17-jdk
OFFICIAL_IMAGE_VERSION := 21-jdk
BASE_IMAGE := $(BASE_IMAGE_NAME):$(BASE_IMAGE_VERSION)

IMAGE_ARCH ?= $(if $(filter arm64 aarch64,$(shell uname -m)),arm64,amd64)
OFFICIAL_IMAGE_ARCH := amd64

JDK_VERSIONS = 17-jdk 21-jdk
ARCH_VERSIONS = amd64 arm64

DOCKER_TAG := $(IMAGE_NAME):$(CAMEL_VERSION)-$(BASE_IMAGE_VERSION)-$(IMAGE_ARCH)

images-build-all:
	for i in $(JDK_VERSIONS); do \
		for j in $(ARCH_VERSIONS); do \
			make IMAGE_ARCH=$$j BASE_IMAGE_VERSION=$$i image-build; \
		done \
	done

image-build:
	@echo "####### Building Camel JBang (CLI) jdk $(BASE_IMAGE_VERSION) arch $(IMAGE_ARCH) container image..."
	docker buildx build \
		--platform=linux/$(IMAGE_ARCH) \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		--build-arg IMAGE_ARCH=$(IMAGE_ARCH) \
		--build-arg JBANG_VERSION=$(JBANG_VERSION) \
		--build-arg CAMEL_VERSION=$(CAMEL_VERSION) \
		--load -t $(DOCKER_TAG) -f build/Dockerfile .
ifeq ($(BASE_IMAGE_VERSION),$(OFFICIAL_IMAGE_VERSION))
ifeq ($(IMAGE_ARCH),$(OFFICIAL_IMAGE_ARCH))
	docker tag $(DOCKER_TAG) $(IMAGE_NAME):$(CAMEL_VERSION)-$(BASE_IMAGE_VERSION)
	docker tag $(DOCKER_TAG) $(IMAGE_NAME):$(CAMEL_VERSION)
endif
	docker tag $(DOCKER_TAG) $(IMAGE_NAME):$(CAMEL_VERSION)-$(IMAGE_ARCH)
else
ifeq ($(IMAGE_ARCH),$(OFFICIAL_IMAGE_ARCH))
	docker tag $(DOCKER_TAG) $(IMAGE_NAME):$(CAMEL_VERSION)-$(BASE_IMAGE_VERSION)
endif
endif

IMAGE_PUSH = $(IMAGE_NAME):$(CAMEL_VERSION)
images-push-all:
	make images-push
	for i in $(JDK_VERSIONS); do \
		make IMAGE_PUSH=$(IMAGE_NAME):$(CAMEL_VERSION)-$$i images-push ; \
	done

images-push:
	@echo "####### Pushing Camel JBang (CLI) $(IMAGE_PUSH) container image..."
	for i in $(ARCH_VERSIONS); do \
		docker push $(IMAGE_PUSH)-$$i ; \
	done
	docker buildx imagetools create -t $(IMAGE_PUSH) \
		$(foreach arch,$(ARCH_VERSIONS),$(IMAGE_PUSH)-$(arch))

images-release: images-build-all images-push-all images-check-all

IMAGE_CHECK = $(IMAGE_NAME):$(CAMEL_VERSION)

images-check-all:
	make image-check
	for i in $(JDK_VERSIONS); do \
		make IMAGE_CHECK=$(IMAGE_NAME):$(CAMEL_VERSION)-$$i image-check ; \
	done

image-check:
	@echo "####### Checking Camel JBang (CLI) $(IMAGE_CHECK) container image..."
	@EXPECTED="$(CAMEL_VERSION)"; \
	ACTUAL=$$(docker run --rm $(IMAGE_CHECK) version | sed -n 's/^Camel CLI version: //p'); \
	test "$$ACTUAL" = "$$EXPECTED"
	@for arch in $(ARCH_VERSIONS); do \
		echo "####### Checking $$arch"; \
		EXPECTED="$(CAMEL_VERSION)"; \
		ACTUAL=$$(docker run --rm --platform linux/$$arch $(IMAGE_CHECK) version | \
			sed -n 's/^Camel CLI version: //p'); \
		test "$$ACTUAL" = "$$EXPECTED" || exit 1; \
	done
