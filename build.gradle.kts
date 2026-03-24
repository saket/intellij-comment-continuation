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
  fun extractPluginDescription(readme: String): String {
    val startMarker = "<!-- Plugin description -->"
    val endMarker = "<!-- Plugin description end -->"

    require(readme.contains(startMarker) && readme.contains(endMarker)) {
      "readme.md must contain $startMarker and $endMarker markers"
    }

    return readme
      .substringAfter(startMarker)
      .substringBefore(endMarker)
      .trim()
  }

  val pluginDescription = providers.fileContents(layout.projectDirectory.file("readme.md"))
    .asText
    .map(::extractPluginDescription)

  pluginConfiguration {
    name = providers.gradleProperty("pluginName")
    version = providers.gradleProperty("pluginVersion")
    description.set(pluginDescription)
    vendor {
      name = providers.gradleProperty("pluginVendor")
    }

    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
      untilBuild = provider { null } // Avoid capping compatibility at the compile-time IDE build.
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
