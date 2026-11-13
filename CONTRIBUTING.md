<!--
 ~ SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
 ~ SPDX-License-Identifier: Apache-2.0
 -->

# Contributing

Issues and pull requests are welcome. By participating you agree to the
[Code of Conduct](CODE_OF_CONDUCT.md). Report security issues as described in
[SECURITY.md](SECURITY.md), not in public issues.

## Development setup

You need JDK 21 and Maven 3.9, or Docker (see [README.md](README.md#build)).

```sh
mvn -B verify
```

Install the [pre-commit](https://pre-commit.com/) hooks once per clone:

```sh
pip install pre-commit
pre-commit install
```

They check file hygiene, [REUSE](https://reuse.software/) compliance and the
commit message on every commit. CI runs the same checks.

## Ground rules

- **Commit messages** follow
  [Conventional Commits](https://www.conventionalcommits.org/), e.g.
  `fix: Skip read-only resources in rich-text reference sync`. Allowed types
  are listed in `.commitlintrc.yml`.
- **Sign off every commit** (`git commit -s`). The `Signed-off-by` line
  certifies the [Developer Certificate of Origin](https://developercertificate.org/).
- **Every new file carries an SPDX header**, e.g. for Java:

  ```java
  // SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
  // SPDX-License-Identifier: Apache-2.0
  ```

  Files that cannot hold a comment get a `<file>.license` sidecar.
  `reuse lint` must pass.
- **Compatibility:** the plugin targets Papyrus 7.x. Keep the
  `Require-Bundle` version ranges in `META-INF/MANIFEST.MF` accurate when you
  use newer APIs.
- **Persisted model data** (annotations stored in user models) must stay
  readable by older versions; changes to its format need a migration path.

## Releases

A maintainer bumps `Bundle-Version` / the POM version and pushes a tag
`v<version>`; the `build` workflow publishes the jar as a GitHub release.
