plugins {
  application
  id("com.diffplug.spotless") version "8.6.0"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories { mavenCentral() }

dependencies {
  val walaVersion = "1.7.2"
  implementation("com.ibm.wala:com.ibm.wala.core:${walaVersion}")
  implementation("com.ibm.wala:com.ibm.wala.util:${walaVersion}")
  implementation("com.ibm.wala:com.ibm.wala.shrike:${walaVersion}")
}

application.mainClass = "com.prawit.deadstore.DeadStoreDetector"

spotless {
  java { googleJavaFormat() }
  kotlinGradle { ktfmt() }
}
