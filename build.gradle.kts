plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.0.21"
  id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
  jvmToolchain(17)
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
    bundledPlugin("com.intellij.java")
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    zipSigner()
  }
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.willowtreeapps.assertk:assertk:0.28.1")
}

tasks.test {
  testLogging {
    showStandardStreams = true
  }
}

intellijPlatform {
  pluginConfiguration {
    name = providers.gradleProperty("pluginName")
    version = providers.gradleProperty("pluginVersion")

    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
    }
  }

  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
  }

  publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
  }
}
