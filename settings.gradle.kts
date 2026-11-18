pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    // Google-hosted mirror of Maven Central. CI runners occasionally get a 404 /
    // timeout from repo.maven.apache.org for short windows; this mirror serves the
    // exact same artifacts from Google's CDN and removes that failure mode.
    maven("https://maven-central.storage-download.googleapis.com/maven2/") {
      content {
        // Google Maven already covers these groups; keep the mirror Central-only.
        excludeGroupByRegex("com\\.android.*")
        excludeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
  }
}

rootProject.name = "BA Screen Record"

include(":app")
