plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.10"
  id("org.metaborg.gradle.config.java-library") version "0.3.10"
  id("org.metaborg.gradle.config.junit-testing") version "0.3.10"
  id("org.metaborg.gitonium") version "0.1.2"
}

sourceSets {
  main {
    java {
      srcDir("src")
    }
  }
  test {
    java {
      srcDir("test")
    }
  }
}

dependencies {
  api("org.sugarj:common:1.7.1")
  api("com.cedarsoftware:java-util-pluto-fixes:1.19.4-SNAPSHOT")
  api("org.yaml:snakeyaml:1.17")
  api("org.objenesis:objenesis:2.2")
  api("org.jetbrains.xodus:xodus-environment:1.0.1")

  testImplementation("org.junit.vintage:junit-vintage-engine:${metaborg.junitVersion}")
}

repositories {
  maven("https://pluto-build.github.io/mvnrepository/")
  maven("https://sugar-lang.github.io/mvnrepository/")
}
