plugins {
  id("java")
  id("jacoco")
}

group = "krilovs.andrejs.app"
version = "1.0-SNAPSHOT"

java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

repositories {
  mavenCentral()
}

val jettyVersion = "12.1.14"
val jacksonVersion = "2.22.4"
val jerseyVersion = "4.0.3"
val agroalVersion = "3.2.1"
val lombokVersion = "1.18.48"
val lombokString = "org.projectlombok:lombok:$lombokVersion"
val mockitoVersion = "5.24.0"
val testcontainersVersion = "2.0.5"

// Mockito inline mock maker is attached as an agent up front, self-attaching is going away in future JDKs
val mockitoAgent: Configuration by configurations.creating

dependencies {
  // Web & Servlet
  implementation("org.eclipse.jetty:jetty-server:$jettyVersion")
  implementation("org.eclipse.jetty.ee11:jetty-ee11-servlet:$jettyVersion")

  // Serialization & Validation
  implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
  implementation("org.glassfish.jersey.ext:jersey-bean-validation:$jerseyVersion")

  // Persistence & Database
  implementation("org.eclipse.persistence:eclipselink:5.0.2") {
    // CVE-2026-92877 (JAXB XXE, no fixed release yet): xjc is a build-time schema compiler and pulls jaxb-core/jaxb-impl
    exclude(group = "com.sun.xml.bind", module = "jaxb-xjc")
  }
  implementation("org.postgresql:postgresql:42.7.14")
  implementation("org.liquibase:liquibase-core:5.0.4")

  // JTA & Connection Pool
  implementation("org.jboss.narayana.jta:narayana-jta:7.3.4.Final")
  implementation("io.agroal:agroal-pool:$agroalVersion")
  implementation("io.agroal:agroal-narayana:$agroalVersion")

  // Logging
  implementation("ch.qos.logback:logback-classic:1.6.5")

  // Lombok
  compileOnly(lombokString)
  annotationProcessor(lombokString)
  testCompileOnly(lombokString)
  testAnnotationProcessor(lombokString)

  // Testing
  testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")

  testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
  mockitoAgent("org.mockito:mockito-core:$mockitoVersion") {
    isTransitive = false
  }

  testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")

  // Transitive Dependency Constraints
  constraints {
    listOf("asm", "asm-commons", "asm-tree", "asm-util", "asm-analysis").forEach { module ->
      implementation("org.ow2.asm:$module:9.11.1") {
        because("EclipseLink transitive ASM < 9.11: JSRInlinerAdapter / AdviceAdapter excessive memory allocation")
      }
    }
    implementation("org.hibernate.validator:hibernate-validator:8.0.6.Final") {
      because("CVE-2026-93879: Hibernate Validator < 8.0.6 EL expression escaping DoS (transitive via jersey-bean-validation)")
    }
    testImplementation("commons-codec:commons-codec:1.23.0") {
      because("CVE-2026-119078: Fix Blake3 KDF vulnerability flagged by JetBrains")
    }
  }
}

tasks.test {
  useJUnitPlatform()
  jvmArgs("-javaagent:${mockitoAgent.asPath}")
  // application.yaml has no default for the password; AppConfigLoaderTest loads it with the real environment
  environment("DB_PASSWORD", "test-password")
  finalizedBy(tasks.jacocoTestReport)
}

val coverageExcludes = listOf("Main*.class")
fun withoutCoverageExcludes(classes: FileCollection) = classes.files.map { dir ->
  fileTree(dir) { exclude(coverageExcludes) }
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required.set(true)
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
  }
  classDirectories.setFrom(withoutCoverageExcludes(classDirectories))
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.jacocoTestReport)
  classDirectories.setFrom(withoutCoverageExcludes(classDirectories))

    violationRules {
      rule {
        enabled = true
        element = "BUNDLE"

        limit {
          counter = "INSTRUCTION"
          value = "COVEREDRATIO"
          minimum = BigDecimal.valueOf(0.8)
        }

        limit {
          counter = "BRANCH"
          value = "COVEREDRATIO"
          minimum = BigDecimal.valueOf(0.8)
        }
      }
    }
}

tasks.named("check") {
  dependsOn(tasks.jacocoTestCoverageVerification)
}