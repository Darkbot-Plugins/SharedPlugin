
plugins {
    id("org.gradle.java-library")
}

buildscript {
    repositories {
        mavenCentral()
    }
}

repositories {
    mavenLocal()
    mavenCentral()

    maven { url = uri("https://jitpack.io") }
}

allprojects {
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

group = "dev.shared"
version = "0.0.0"
description = "SharedPlugin"

dependencies {
    api("eu.darkbot.DarkBotAPI", "darkbot-impl", "0.9.11")
    api("eu.darkbot", "DarkBot", "e787b48c23")
}


tasks.named<Jar>("jar") {
    archiveFileName.set("SharedPlugin.jar")
}

tasks.register<Exec>("signFile") {
    dependsOn("build")
    commandLine("cmd", "/c", "sign.bat")
}