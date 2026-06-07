# Contributing to TachiyomiDNP Android

Thank you for your interest in contributing! This document outlines how to contribute effectively.

## Getting Started

1. **Fork** the repository
2. **Clone** your fork: `git clone https://github.com/YOUR_USERNAME/TachiyomiDNP-Android.git`
3. **Create a branch**: `git checkout -b feature/your-feature-name`
4. **Open** the project in Android Studio

## Development Guidelines

### Code Style
- Follow Kotlin style conventions (ktlint is configured)
- Use meaningful variable and function names
- Keep functions small and focused
- Add KDoc comments for public APIs

### Commit Messages
- Use present tense: "Add feature" not "Added feature"
- Use imperative mood: "Move cursor to..." not "Moves cursor to..."
- Limit the first line to 72 characters
- Reference issues and PRs where appropriate

### Pull Request Process
1. Update the README.md with details of changes if applicable
2. Update the CHANGELOG.md with your changes under `[Unreleased]`
3. Ensure your code builds without errors (`./gradlew assembleDebug`)
4. Link any related issues in the PR description
5. A maintainer will review and merge when ready

## Project Structure

| Directory | Purpose |
|-----------|---------|
| `app/` | Main application module — source code, resources, build config |
| `buildSrc/` | Build logic — versions, plugins, dependencies |
| `.github/` | GitHub Actions CI, issue templates |

## Building

```bash
export JAVA_HOME=~/jdk-17.0.11+9
export PATH=$JAVA_HOME/bin:$PATH

./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build
```

## Testing

- Run unit tests: `./gradlew test`
- Run instrumented tests: `./gradlew connectedAndroidTest`
- Test on physical devices when possible
- Verify on multiple Android versions (API 23, 29, 34+)

## Reporting Bugs

When reporting bugs, please include:
- Device model and Android version
- App version (Settings → About → Version)
- Steps to reproduce
- Expected vs actual behavior
- Screenshots or screen recordings if applicable
- Crash logs if available

## Feature Requests

- Search existing issues first
- Describe the feature clearly with use cases
- Explain why it would benefit users
- Consider if it aligns with TachiyomiJ2K's philosophy

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
