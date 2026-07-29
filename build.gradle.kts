plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

group = "io.github.melmedany"
version = "1.0"

springBoot {
    mainClass = "io.workflowai.bootstrap.WorkflowAIApplication"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// override spring boot flyway version
extra["flyway.version"] = libs.versions.flyway.get()

dependencies {
    implementation(platform(libs.langchain4j.bom))
    testImplementation(platform(libs.junit.bom))
    testImplementation(platform(libs.testcontainers.bom))

    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.restclient)
    implementation(libs.spring.boot.data)
    implementation(libs.spring.boot.flyway)
    implementation(libs.spring.boot.validation)
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.ollama)
    implementation(libs.langchain4j.openai)
    implementation(libs.langchain4j.anthropic)
    implementation(libs.langgraph4j.core)

    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.restassured)
    testImplementation(libs.archunit)
}

tasks.withType<Test> {
    useJUnitPlatform()
}