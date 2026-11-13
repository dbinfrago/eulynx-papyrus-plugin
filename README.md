<!--
 ~ SPDX-FileCopyrightText: Copyright DB InfraGO AG and contributors
 ~ SPDX-License-Identifier: Apache-2.0
 -->

# EULYNX Papyrus Plugin

Eclipse Papyrus plugin (`com.eulynx.papyrus.specification`) with the tooling
used to write the EULYNX SysML specification:
- EULYNX label rendering and editing for state machines, messages,
  operations, requirements and duration constraints
- "Sync State Machine to Requirements" into "&lt;name&gt; - Requirements"
  packages
- Rich-text comment editing and preview with reference sync
- Diagram frames, label width and name wrapping
- Export of requirements, transition information, tables and all diagrams

## Compatibility

Built against Papyrus Desktop 7.1.0 (Eclipse 2025-06) with Java 21.
The `Require-Bundle` version ranges in `META-INF/MANIFEST.MF` accept any
Papyrus 7.x.

## Build

```sh
mvn -B verify
```

Without a local Maven and JDK 21:

```sh
docker run --rm -v "$PWD:/w" -w /w -v ~/.m2:/root/.m2 \
  maven:3.9-eclipse-temurin-21 mvn -B verify
```

The plugin jar is written to `target/`.

## Install

Download `com.eulynx.papyrus.specification_<version>.jar` from the
[releases](https://github.com/dbinfrago/eulynx-papyrus-plugin/releases), copy
it into `<papyrus>/dropins/` and restart Papyrus.

## Release

Push a tag `v<version>`. The `build` workflow builds that version and attaches
the jar to a GitHub release.

## Licence

Apache-2.0, see [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt). The
project is [REUSE](https://reuse.software/) compliant.
