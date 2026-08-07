#!/bin/bash
set -e
if [ -d /tmp/wala-stdlib/ ]; then rm -rf /tmp/wala-stdlib* ;fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

#JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home"
JAVA_VERSION=$("$JAVA_HOME/bin/java" --version | awk 'NR==1{split($2,a,"."); print a[1]}')
sed -i '' "s/JavaLanguageVersion\.of(\([0-9]*\.*\)*)/JavaLanguageVersion.of($JAVA_VERSION)/" "$SCRIPT_DIR/build.gradle.kts"
echo "=== build.gradle.kts toolchain set to Java $JAVA_VERSION ==="

"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --stop
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --version | grep JVM
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" clean compileJava


BUILD_DIR="$(cd "$SCRIPT_DIR" && realpath "../../../test-app/wala/fibo/target/classes/com/sirisuk")"
SOURCE_DIR="$(cd "$SCRIPT_DIR" && realpath "../../../test-app/wala/fibo/src/main/java")"
TARGET="$(cd "$SCRIPT_DIR" && realpath "../../../test-app/wala/fibo/target/classes/com/sirisuk/Main.class")"
TARGET_JS="$(cd "$SCRIPT_DIR" && realpath "src/main/resources/test-files/fibo.js")"

if [ ! -f "$TARGET" ] ; then echo "ERROR: target not found: $TARGET"; exit 1;fi;echo "=== Target: $TARGET ==="
if [ ! -f "$TARGET_JS" ]; then echo "ERROR: target js not found: $TARGET_JS"; exit 1;fi;echo "=== Target: $TARGET_JS ==="

# --- ScopeFile ---
# Requires a WALA scope file (text format: Loader,Lang,type,path per line)
SCOPE_FILE="$SCRIPT_DIR/scope.txt"

# Build scope file pointing to the target class
# stdlib,none tells WALA to use the running JVM's bootstrap classes (Java 9+ compatible)
{
  echo "Primordial,Java,stdlib,none"
#  echo "Primordial,Java,jarFile,primordial.jar.model"
#  echo "Application,Java,classFile,$TARGET"
  echo "Application,Java,binaryDir,$BUILD_DIR"
} > "$SCOPE_FILE"

JDK_HOME="${JAVA_HOME:?JAVA_HOME must be set}"

if [ ! -f /tmp/wala-stdlib/java.base.jar ]; then
    echo "Building WALA stdlib cache (one-time)..."
    mkdir -p /tmp/wala-stdlib/x
    unzip -q "$JDK_HOME/jmods/java.base.jmod" -d /tmp/wala-stdlib/x || true
    "$JDK_HOME/bin/jar" cf /tmp/wala-stdlib/java.base.jar \
        -C /tmp/wala-stdlib/x/classes .
    rm -rf /tmp/wala-stdlib/x
fi

if [ ! -f /tmp/wala-stdlib/.libs-ready ]; then
    echo "Copying WALA + ECJ dependency JARs (one-time)..."
    find ~/.gradle/caches/modules-2/files-2.1/com.ibm.wala \
        -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
        -exec cp {} /tmp/wala-stdlib/  \;
    find ~/.gradle/caches/modules-2/files-2.1/org.eclipse.jdt \
        -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
        -exec cp {} /tmp/wala-stdlib/ \;
    touch /tmp/wala-stdlib/.libs-ready
fi

## Test script below this section
## --- ScopeFileCallGraph ---
#"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
#  -PmainClass=com.ibm.wala.examples.drivers.ScopeFileCallGraph \
#  --args="-scopeFile $SCOPE_FILE -mainClass Lcom/sirisuk/Main"

# --- DecompileCFG ---
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" run \
  -PmainClass=com.sirisuk.Main \
  --args="-scopeFile $SCOPE_FILE -mainClass Lcom/sirisuk/Main -sourceDir $SOURCE_DIR"
