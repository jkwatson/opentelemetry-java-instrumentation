plugins {
  id("otel.library-instrumentation")
}


dependencies {
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}
