# M00n JUnit 5 Reporter

[![Maven Central](https://img.shields.io/maven-central/v/io.m00nreport/m00n-junit5-reporter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:io.m00nreport%20a:m00n-junit5-reporter)
[![JitPack](https://jitpack.io/v/m00nsolutions/m00n-junit5-reporter.svg)](https://jitpack.io/#m00nsolutions/m00n-junit5-reporter)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A JUnit 5 reporter for [M00n Report](https://app.m00n.report) that streams test results in real-time.

## ✨ Features

- 🚀 **Real-time streaming** - Watch tests as they run via WebSocket
- 📝 **Automatic step tracking** - `@Step` annotation with Page Objects
- 🔄 **Retry tracking** - Full support for `@RetryingTest`
- 📸 **Rich attachments** - Screenshots, videos, traces
- 🎭 **Playwright ready** - Built-in browser testing integration
- ⚡ **Thread-safe** - Parallel test execution support

## 🚀 Quick Start

### 1. Add Dependency

**Gradle (Kotlin DSL):**

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.m00nreport:m00n-junit5-reporter:1.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}
```

**Maven:**

```xml
<dependency>
    <groupId>io.m00nreport</groupId>
    <artifactId>m00n-junit5-reporter</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### 2. Configure

Create `src/test/resources/m00n.properties`:

```properties
m00n.serverUrl=https://app.m00n.report
m00n.apiKey=m00n_your_api_key_here
m00n.launch=My Test Suite
```

Create `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

### 3. Run Tests

```bash
./gradlew test
```

Results stream to M00n Report in real-time! 🎉

## 📖 Full Documentation

See the [full documentation](m00n-junit5-reporter/README.md) for:

- Playwright integration with step tracking
- Page Object pattern with `@Step` annotation
- Base test class for automatic artifact capture
- Retry support with JUnit Pioneer
- Attachments API
- Configuration reference

## 📁 Project Structure

```
m00n-junit5-reporter/
├── m00n-junit5-reporter/     # The reporter library
│   ├── src/main/java/        # Source code
│   └── README.md             # Full documentation
├── examples/                 # Example tests
│   └── src/test/java/        # Test examples
└── README.md                 # This file
```

## 🔧 Building from Source

```bash
# Clone the repository
git clone https://github.com/m00nsolutions/m00n-junit5-reporter.git
cd m00n-junit5-reporter

# Build
./gradlew build

# Publish to local Maven
./gradlew publishToMavenLocal

# Run examples
./gradlew :examples:test
```

## 📝 License

MIT License - See [LICENSE](LICENSE) for details.

## 🔗 Links

- [M00n Report Dashboard](https://app.m00n.report)
- [Documentation](https://app.m00n.report/documentation/reporters/junit5)
- [Issue Tracker](https://github.com/m00nsolutions/m00n-junit5-reporter/issues)
