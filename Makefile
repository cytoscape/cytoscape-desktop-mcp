.PHONY: clean test build lint lint-fix coverage install install-release updateversion build_claude_mcpb help
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
	@echo "  build_claude_mcpb  package claude-extension/ into build/cytoscape-mcp.mcpb"

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

build_claude_mcpb:
	rm -rf build/mcpb-staging build/cytoscape-mcp.mcpb
	mkdir -p build/mcpb-staging
	cp claude-extension/manifest.json build/mcpb-staging/manifest.json
	cp claude-extension/icon.png build/mcpb-staging/icon.png
	cp -r claude-extension/server build/mcpb-staging/server
	cd build/mcpb-staging && zip -r ../cytoscape-mcp.mcpb .
	@echo "Built build/cytoscape-mcp.mcpb"
