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

.PHONY: multi-arch-package
multi-arch-package: build
	docker build -t $(REPO):$(TAG)-amd64 --build-arg ARCH=amd64 -f docker/Dockerfile.core ./docker
	docker build -t $(REPO):$(TAG)-arm32v6 --build-arg ARCH=arm32v7 -f docker/Dockerfile.core ./docker
	docker build -t $(REPO):$(TAG)-arm64v8 --build-arg ARCH=arm64v8 -f docker/Dockerfile.core ./docker

.PHONY: multi-arch-publish
multi-arch-publish: multi-arch-package
	docker push $(REPO):$(TAG)-amd64
	docker push $(REPO):$(TAG)-arm32v6
	docker push $(REPO):$(TAG)-arm64v8
	docker manifest create $(REPO):$(TAG) \
		$(REPO):$(TAG)-amd64 \
		$(REPO):$(TAG)-arm32v6 \
		$(REPO):$(TAG)-arm64v8
	docker manifest push --purge $(REPO):$(TAG)