.PHONY: lint lint-fix check build

lint-fix:
	./gradlew ktlintFormat
lint:
	./gradlew ktlintCheck
build:
	./build.sh
check:
	./gradlew detekt
