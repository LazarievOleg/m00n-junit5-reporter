# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-01-14

### Added
- **AspectJ-based step tracking** - Automatic `@Step` interception without StepProxy or PageFactory
- `M00nStepAspect` for load-time weaving of `@Step` annotated methods
- Zero-boilerplate step tracking - just annotate methods and they're tracked!

### Changed
- Recommended approach is now AspectJ (simpler, no factory classes needed)
- Updated documentation to reflect AspectJ-first approach
- Updated all examples to use concrete classes instead of interfaces

### Deprecated
- `StepProxy.create()` - still works but AspectJ is recommended
- Interface-based Page Objects - concrete classes are simpler with AspectJ

## [1.0.0] - 2026-01-14

### Added
- Initial release of M00n JUnit 5 Reporter
- Real-time test result streaming via WebSocket
- JUnit 5 extension with automatic registration via ServiceLoader

### Step Tracking
- `@Step` annotation for Page Object methods
- `StepProxy.create()` for manual step tracking (legacy approach)
- Steps appear in M00n Report with timing and status
- Manual step API via `TestResult.addStep()`

### Retry Support
- Full support for JUnit Pioneer's `@RetryingTest`
- Each retry attempt tracked separately
- Flaky test detection and marking

### Attachments
- Screenshot attachment via `AttachmentData.screenshot()`
- Video attachment via `AttachmentData.video()`
- Trace attachment via `AttachmentData.trace()`
- Log attachment via `AttachmentData.log()`
- Generic file attachment via `AttachmentData.file()`
- Async upload with retry logic

### Playwright Integration
- `M00nStep.current()` for current test access
- `M00nStep.isCurrentTestFailed()` for conditional artifact capture
- Base test class pattern for artifact capture

### Configuration
- `m00n.properties` file configuration
- Environment variable support
- System property overrides
- Multiple configuration sources with precedence

### Properties
| Property | Description |
|----------|-------------|
| `m00n.serverUrl` | Ingest server URL (required) |
| `m00n.apiKey` | Project API key (required) |
| `m00n.enabled` | Enable/disable reporter |
| `m00n.launch` | Run name |
| `m00n.tags` | Comma-separated tags |
| `m00n.debug` | Enable debug logging |
| `m00n.timeout` | HTTP timeout (ms) |
| `m00n.maxRetries` | Max retry attempts |
| `m00n.attribute.*` | Custom attributes |

### Other
- Thread-safe parallel test execution
- `@DisplayName` support for readable test titles
- Graceful error handling
- Connection pooling for HTTP client
- AtomicInteger counters for thread safety

[Unreleased]: https://github.com/m00nsolutions/m00n-junit5-reporter/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/m00nsolutions/m00n-junit5-reporter/releases/tag/v1.0.0
