GRADLEW   := ./gradlew
APK_DEBUG := app/build/outputs/apk/debug/app-debug.apk
APK_REL   := app/build/outputs/apk/release/app-release.apk

.PHONY: build release bundle clean install uninstall lint test check help

## build   — assemble debug APK
build:
	$(GRADLEW) assembleDebug

## release — assemble signed release APK (requires KEYSTORE_PATH / KEY_* env vars)
release:
	$(GRADLEW) assembleRelease

## bundle  — build release AAB for Play Store upload
bundle:
	$(GRADLEW) bundleRelease

## clean   — delete all build artefacts
clean:
	$(GRADLEW) clean

## install — build & install debug APK on connected device / emulator
install: build
	adb install -r $(APK_DEBUG)

## uninstall — remove the app from connected device
uninstall:
	adb uninstall com.drivetracker

## lint    — run Android lint checks
lint:
	$(GRADLEW) lint

## test    — run unit tests
test:
	$(GRADLEW) test

## check   — lint + unit tests in one shot
check: lint test

## help    — list available targets
help:
	@grep -E '^##' Makefile | sed 's/## /  /'
