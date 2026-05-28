# Contributing

## Scope

This repository contains two Java 17 Maven modules that implement rate limiting for Camunda 8 / Zeebe process creation traffic.

## Before you change code

Review:

- [README.md](README.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/CONFIGURATION.md](docs/CONFIGURATION.md)
- [docs/TESTING.md](docs/TESTING.md)

## Local workflow

1. Build the interceptor module
2. Build the filter module
3. Run the local Docker Compose stack
4. Verify both REST and gRPC behavior manually

## Contribution priorities

Good next improvements for this repository include:

- externalizing rate-limit settings
- adding automated tests
- improving JWT parsing and validation
- supporting shared rate limits across multiple instances
- documenting production deployment patterns

## Pull request expectations

When contributing:

- keep changes focused
- update documentation when behavior changes
- preserve compatibility with the current Docker Compose example
- include validation notes in the PR description
