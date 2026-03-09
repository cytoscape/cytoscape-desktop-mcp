.PHONY: clean test build lint lint-fix coverage install install-release updateversion help
.DEFAULT_GOAL := help

GRADLEW := ./gradlew

help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  clean            run gradle clean"
	@echo "  test             run tests with gradle test"
	@echo "  build            build the OSGi bundle JAR"
	@echo "  lint             check code formatting with Spotless"
	@echo "  lint-fix         auto-fix Spotless formatting"
	@echo "  coverage         run tests and generate JaCoCo report (build/reports/jacoco/test/html/index.html)"
	@echo "  install          install to local Maven repository"
	@echo "  install-release  build release JAR with VERSION (e.g. make install-release VERSION=1.2.3)"
	@echo "  updateversion    update project version in build.gradle"

clean:
	$(GRADLEW) clean

test:
	$(GRADLEW) test

lint:
	$(GRADLEW) spotlessCheck

lint-fix:
	$(GRADLEW) spotlessApply

coverage:
	$(GRADLEW) jacocoTestReport

build: clean
	$(GRADLEW) jar

install: clean
	$(GRADLEW) publishToMavenLocal

install-release:
	@if [ -z "$(VERSION)" ]; then echo "Error: VERSION is required. Usage: make install-release VERSION=1.2.3"; exit 1; fi
	$(GRADLEW) clean jar -PreleaseVersion=$(VERSION)

updateversion:
	@echo "Edit the 'version' property in build.gradle directly."
