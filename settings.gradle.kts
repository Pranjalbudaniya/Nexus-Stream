pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven { url = uri("https://dl.frostwire.com/maven") } }
            filter { includeGroup("com.frostwire") }
        }
    }
}

rootProject.name = "NexusStream"
include(":app")
