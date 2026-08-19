# Cosmos DB Change Feed Processor tutorial.
# `make help` lists everything.

SHELL := /bin/bash
MVN   := ./mvnw
JAVA  := java

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

.PHONY: jdk
jdk: ## Show the JDK jenv has selected for this project
	@jenv version
	@$(JAVA) -version

.PHONY: build
build: ## Compile and package all modules
	$(MVN) -B clean package

.PHONY: test
test: ## Run unit tests
	$(MVN) -B test

.PHONY: verify
verify: ## Full quality gate: tests + spotless check + jacoco
	$(MVN) -B clean verify

.PHONY: format
format: ## Auto-format with google-java-format
	$(MVN) -B spotless:apply

.PHONY: lint
lint: ## Check formatting without changing files
	$(MVN) -B spotless:check

.PHONY: coverage
coverage: verify ## Open the JaCoCo report
	@open cfp-common/target/site/jacoco/index.html 2>/dev/null || true

# --- Apps ------------------------------------------------------------------

.PHONY: provision
provision: ## Create the Cosmos account, database and containers
	$(JAVA) -jar cfp-admin/target/cfp-admin.jar provision

.PHONY: info
info: ## Show account regions, consistency and document counts
	$(JAVA) -jar cfp-admin/target/cfp-admin.jar info

.PHONY: links
links: ## Print Azure Portal / Data Explorer links
	$(JAVA) -jar cfp-admin/target/cfp-admin.jar links

.PHONY: teardown
teardown: ## Delete the Cosmos account (prompts for confirmation)
	$(JAVA) -jar cfp-admin/target/cfp-admin.jar teardown

.PHONY: writer
writer: ## Run the writer web app on http://localhost:8080
	$(JAVA) -jar cfp-writer/target/cfp-writer.jar

.PHONY: reader
reader: ## Run the CFP reader from the current time
	$(JAVA) -jar cfp-reader/target/cfp-reader.jar run

.PHONY: reader-replay
reader-replay: ## Run the CFP reader from the beginning of the change feed
	$(JAVA) -jar cfp-reader/target/cfp-reader.jar run --start-from=beginning \
	  --lease-prefix=replay-$$(date +%s)

.PHONY: leases
leases: ## List the CFP lease documents
	$(JAVA) -jar cfp-reader/target/cfp-reader.jar leases

.PHONY: leases-purge
leases-purge: ## Delete all lease documents (forces a fresh start)
	$(JAVA) -jar cfp-reader/target/cfp-reader.jar leases --purge

.PHONY: clean
clean: ## Remove build output
	$(MVN) -B clean
