# Release mechanics mirrored from Jellio-Plugin's own Makefile
# (itself taken as-is from Gelato, https://github.com/lostb1t/Gelato,
# GPL-3.0), adapted here to bump app/build.gradle.kts's own
# versionName/versionCode instead of a plugin manifest.

release:
	@echo "Fetching tags..."
	git fetch --tags
	@echo "Bumping version with git-cliff..."
	$(eval NEW_VERSION := $(shell git cliff --bumped-version))
	$(eval VERSION_NAME := $(NEW_VERSION:v%=%))
	@echo "New version will be: $(NEW_VERSION)"
	@echo "Generating changelog..."
	@git cliff --unreleased --tag $(NEW_VERSION) --strip all > /tmp/release_notes.md
	@echo "Updating versionName in app/build.gradle.kts..."
	sed -i 's/versionName = "[^"]*"/versionName = "$(VERSION_NAME)"/' app/build.gradle.kts
	git add app/build.gradle.kts
	@if git diff --cached --quiet; then \
		echo "app/build.gradle.kts already at $(VERSION_NAME), nothing to commit."; \
	else \
		git commit -m "chore(release): bump version to $(NEW_VERSION)"; \
		echo "Pushing to git..."; \
		git push; \
	fi
	@echo "Creating GitHub release..."
	gh release create $(NEW_VERSION) --title "$(NEW_VERSION)" --notes-file /tmp/release_notes.md
	@echo "Release $(NEW_VERSION) created successfully!"

test:
	@echo "Fetching tags..."
	git fetch --tags
	@echo "Bumping version with git-cliff..."
	$(eval NEW_VERSION := $(shell git cliff --bumped-version))
	@echo "New version will be: $(NEW_VERSION)"
	@echo "Generating changelog..."
	@git cliff --unreleased --tag $(NEW_VERSION) --strip all > /tmp/release_notes.md
	@cat /tmp/release_notes.md

.PHONY: release test
