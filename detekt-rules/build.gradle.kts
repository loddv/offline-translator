plugins {
  alias(libs.plugins.kotlin.jvm)
}

dependencies {
  compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.4")

  testImplementation("io.gitlab.arturbosch.detekt:detekt-test:1.23.4")
  testImplementation(libs.junit)
}

tasks.withType<Test> {
  useJUnit()
}
