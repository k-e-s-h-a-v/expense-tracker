install:
	 ./gradlew clean; ./gradlew installDebug --stacktrace

build:
	./gradlew clean; ./gradlew assembleDebug --stacktrace


release:
	./gradlew clean; ./gradlew assembleRelease --stacktrace