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
val mapstructVersion = "1.6.3"
val lombokVersion = "1.18.48"
val lombokString = "org.projectlombok:lombok:$lombokVersion"
val mockitoVersion = "5.24.0"

// Mockito inline mock maker is attached as an agent up front, self-attaching is going away in future JDKs
val mockitoAgent: Configuration by configurations.creating

dependencies {
  implementation("org.eclipse.jetty:jetty-server:$jettyVersion")
  implementation("org.eclipse.jetty.ee11:jetty-ee11-servlet:$jettyVersion")

  implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
  implementation("org.glassfish.jersey.ext:jersey-bean-validation:$jerseyVersion")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")

  implementation("org.eclipse.persistence:eclipselink:5.0.2") {
    // CVE-2026-92877 (JAXB XXE, no fixed release yet): xjc is a build-time schema compiler and pulls jaxb-core/jaxb-impl
    exclude(group = "com.sun.xml.bind", module = "jaxb-xjc")
  }
  // eclipselink pulls ASM transitively; force a version past the asm-commons DoS fixes (fixed in 9.11)
  constraints {
    listOf("asm", "asm-commons", "asm-tree", "asm-util", "asm-analysis").forEach {
      implementation("org.ow2.asm:$it:9.11.1") {
        because("asm-commons < 9.11: JSRInlinerAdapter / AdviceAdapter excessive memory allocation")
      }
    }
  }
  implementation("org.postgresql:postgresql:42.7.14")
  implementation("org.liquibase:liquibase-core:5.0.4")

  implementation("org.jboss.narayana.jta:narayana-jta:7.3.4.Final")
  implementation("io.agroal:agroal-pool:$agroalVersion")
  implementation("io.agroal:agroal-narayana:$agroalVersion")

  implementation(lombokString)
  testCompileOnly(lombokString)
  annotationProcessor(lombokString)
  testAnnotationProcessor(lombokString)
  annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
  annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
  implementation("org.mapstruct:mapstruct:${mapstructVersion}")

  implementation("ch.qos.logback:logback-classic:1.6.5")

  testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
  testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
  mockitoAgent("org.mockito:mockito-core:$mockitoVersion") { isTransitive = false }
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.test {
  useJUnitPlatform()
  jvmArgs("-javaagent:${mockitoAgent.asPath}")
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required = true
    html.required = true
    html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
  }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.jacocoTestReport)

    violationRules {
      rule {
        enabled = true
        element = "BUNDLE"

        limit {
          counter = "INSTRUCTION"
          value = "COVEREDRATIO"
          minimum = BigDecimal.ZERO
        }

        limit {
          counter = "BRANCH"
          value = "COVEREDRATIO"
          minimum = BigDecimal.ZERO
        }
      }
    }
}

tasks.named("check") {
  dependsOn(tasks.jacocoTestCoverageVerification)
}