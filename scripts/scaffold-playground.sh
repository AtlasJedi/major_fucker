#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

STACK="${1:-kotlin}"

case "$STACK" in
  kotlin|java)
    if [ -f "playground/build.gradle.kts" ]; then
      echo "Kotlin/Java playground already exists. Skipping."
      exit 0
    fi
    echo "Kotlin playground ships with the repo. If missing, re-clone."
    ;;
  python)
    if [ -f "playground/pyproject.toml" ]; then
      echo "Python playground already exists. Skipping."
      exit 0
    fi
    if [ -d "playground" ] && [ -f "playground/build.gradle.kts" ]; then
      mv playground playground.bak
      echo "Backed up existing playground to playground.bak/"
    fi
    mkdir -p playground/src playground/tests
    cat > playground/pyproject.toml << 'PYEOF'
[project]
name = "major-playground"
version = "0.1.0"
requires-python = ">=3.11"
[tool.pytest.ini_options]
testpaths = ["tests"]
PYEOF
    touch playground/tests/conftest.py
    echo "Python playground scaffolded. Run: cd playground && pip install -e '.[dev]'"
    ;;
  typescript|ts)
    if [ -f "playground/package.json" ]; then
      echo "TypeScript playground already exists. Skipping."
      exit 0
    fi
    if [ -d "playground" ] && [ -f "playground/build.gradle.kts" ]; then
      mv playground playground.bak
      echo "Backed up existing playground to playground.bak/"
    fi
    mkdir -p playground/src playground/tests
    cat > playground/package.json << 'PKGJSON'
{
  "name": "major-playground",
  "version": "0.1.0",
  "type": "module",
  "scripts": {"test": "vitest run", "test:watch": "vitest"},
  "devDependencies": {"vitest": "^2.0.0", "typescript": "^5.6.0"}
}
PKGJSON
    cat > playground/tsconfig.json << 'TSCONF'
{"compilerOptions": {"target": "ES2022", "module": "ESNext", "moduleResolution": "bundler", "strict": true, "outDir": "dist"}, "include": ["src", "tests"]}
TSCONF
    echo "TypeScript playground scaffolded. Run: cd playground && npm install"
    ;;
  go|golang)
    if [ -f "playground/go.mod" ]; then
      echo "Go playground already exists. Skipping."
      exit 0
    fi
    if [ -d "playground" ] && [ -f "playground/build.gradle.kts" ]; then
      mv playground playground.bak
      echo "Backed up existing playground to playground.bak/"
    fi
    mkdir -p playground
    cat > playground/go.mod << 'GOMOD'
module major-playground
go 1.22
GOMOD
    echo "Go playground scaffolded."
    ;;
  *)
    echo "Unknown stack: $STACK. Creating empty playground/"
    mkdir -p playground/src playground/tests
    ;;
esac
