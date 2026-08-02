.PHONY: install build release release-install

install:
	 ./gradlew clean; ./gradlew installDebug --stacktrace

build:
	./gradlew clean; ./gradlew assembleDebug --stacktrace


release:
	./gradlew clean; ./gradlew assembleRelease --stacktrace

release-install:
	./gradlew clean; ./gradlew assembleRelease --stacktrace; adb install -r app/build/outputs/apk/release/app-release.apk