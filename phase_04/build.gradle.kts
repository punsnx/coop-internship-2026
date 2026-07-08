plugins {
  application
  id("com.diffplug.spotless") version "8.6.0"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories { mavenCentral() }

dependencies { implementation("com.github.javaparser:javaparser-core:3.26.4") }

application.mainClass = "com.prawit.deadstore.DeadStoreDetector"

spotless {
  java { googleJavaFormat() }
  kotlinGradle { ktfmt() }
}
