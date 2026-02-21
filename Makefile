.PHONY: clean test build install install-release updateversion help
.DEFAULT_GOAL := help

GRADLEW := ./gradlew

help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  clean            run gradle clean"
	@echo "  test             run tests with gradle test"
	@echo "  build            build the OSGi bundle JAR"
	@echo "  install          install to local Maven repository"
	@echo "  install-release  build release JAR with VERSION (e.g. make install-release VERSION=1.2.3)"
	@echo "  updateversion    update project version in build.gradle"

clean:
	$(GRADLEW) clean

test: clean
	$(GRADLEW) test

build: clean
	$(GRADLEW) jar

install: clean
	$(GRADLEW) publishToMavenLocal

install-release:
	@if [ -z "$(VERSION)" ]; then echo "Error: VERSION is required. Usage: make install-release VERSION=1.2.3"; exit 1; fi
	$(GRADLEW) clean jar -Pversion=$(VERSION)

updateversion:
	@echo "Edit the 'version' property in build.gradle directly."
