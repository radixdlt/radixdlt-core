# (C) Copyright 2020 Radix DLT Ltd
#
# Radix DLT Ltd licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except in
# compliance with the License.  You may obtain a copy of the
# License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
# either express or implied.  See the License for the specific
# language governing permissions and limitations under the License.

REGISTRY ?= eu.gcr.io/dev-container-repo

all:
    $(eval GIT_BRANCH=$(shell git rev-parse --abbrev-ref HEAD | sed 's/\//-/g'))
    $(eval GIT_COMMIT=$(shell git log -1 --format=%h ))
    TAG ?= $(GIT_BRANCH)-$(GIT_COMMIT)
    REPO ?= $(REGISTRY)/radixdlt-core

.PHONY: build
build:
	./gradlew deb4docker

.PHONY: package
package: build
	docker-compose -f docker/single-node.yml build
	docker tag radixdlt/radixdlt-core:develop $(REPO):$(TAG)

.PHONY: publish
publish: package
	docker push $(REPO):$(TAG)