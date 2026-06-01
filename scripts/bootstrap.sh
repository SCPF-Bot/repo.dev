#!/bin/sh
# Run this once after cloning to generate gradle/wrapper/gradle-wrapper.jar
# Requires Gradle to be installed: https://gradle.org/install/
set -e
echo "Generating Gradle wrapper..."
gradle wrapper --gradle-version 8.2 --distribution-type bin
echo "Done. You can now use ./gradlew"
