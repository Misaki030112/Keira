# Keira AI Assistant Minecraft Fabric Mod

Keira is a Minecraft Fabric mod that provides an AI assistant for gameplay, built with Java 21, Spring AI framework, and Fabric Loom. It supports multiple AI providers (OpenAI, DeepSeek, Anthropic) and offers in-game chat, memory persistence, and various player assistance tools.

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Bootstrap and Build Process
Set up the development environment and build the mod:

```bash
# CRITICAL: Install Java 21 (mod requirement - NEVER use Java 17)
sudo apt update && sudo apt install -y openjdk-21-jdk
export PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

Build and verify:
- `./gradlew clean --no-daemon` -- **NEVER CANCEL: Takes 7 seconds. Set timeout to 2+ minutes.**
- `./gradlew clean build --no-daemon` -- **NEVER CANCEL: Takes 30 seconds with cache, 2+ minutes without. Set timeout to 5+ minutes.**
- `./gradlew check --no-daemon` -- **NEVER CANCEL: Takes 8 seconds. Set timeout to 2+ minutes.**
- `./gradlew test --no-daemon` -- **NEVER CANCEL: Takes 8 seconds. Set timeout to 2+ minutes.** (Note: No actual tests exist, only validates compilation)
- `./gradlew sourcesJar --no-daemon` -- **NEVER CANCEL: Takes 8 seconds. Set timeout to 2+ minutes.**

### Development and Testing
Run the mod in development:
- **Client**: `./gradlew runClient --no-daemon` -- **NEVER CANCEL: Takes 30+ seconds to start Minecraft client. Set timeout to 10+ minutes.**
  - Client runs with full Minecraft GUI (not accessible in headless environment)
  - Loads mod successfully with Fabric 1.21.8
  - Use `timeout 30s` for brief testing, expect normal startup messages
- **Server**: `./gradlew runServer --no-daemon` -- **NEVER CANCEL: Takes 20 seconds to start server. Set timeout to 5+ minutes.**
  - Server starts successfully but requires EULA acceptance for full operation
  - SUCCESSFUL VALIDATION: Look for "🤖 Keira Mod is loading...", "✅ Command registration callback attached", "✨ Keira Mod loaded!"
  - NORMAL COMPLETION: Server stops with "You need to agree to the EULA" message

## Validation

### Manual Testing Requirements
**ALWAYS** manually validate any new code through these scenarios after making changes:
1. **Build Verification**: Run `./gradlew clean build` and verify successful completion without errors (takes 2 minutes)
2. **Mod Loading**: Test `./gradlew runServer` briefly (20 seconds) to verify mod loads without crash
3. **Success Indicators**: Verify these exact log messages appear during server startup:
   - `[main/INFO] (keira) 🤖 Keira Mod is loading...`
   - `[main/INFO] (keira) ✅ Command registration callback attached`
   - `[main/INFO] (keira) ✨ Keira Mod loaded!`
4. **Expected Completion**: Server should stop with EULA message - this is NORMAL and indicates successful mod loading
5. **Configuration Changes**: If modifying AI or config code, check that no startup errors occur during mod initialization

### Critical Build Requirements
- **ALWAYS** run `./gradlew clean build` before considering any change complete
- **ALWAYS** verify the mod jar is generated in `build/libs/` (approximately 23MB main jar, 1.6MB sources jar)
- **NEVER** commit code that doesn't build successfully
- **ALWAYS** use Java 21 - the project will fail with other Java versions
- **ALWAYS** look for the 🤖 emoji and checkmark ✅ symbols in server logs to confirm mod loaded properly

## Dependencies and Configuration

### Key Dependencies (from gradle.properties)
- Minecraft: 1.21.8
- Fabric Loader: 0.17.2  
- Fabric API: 0.131.0+1.21.8
- Yarn Mappings: 1.21.8+build.1
- Java: 21 (REQUIRED)

### AI Configuration Requirements
The mod requires AI provider configuration to function:
- Supports OpenAI, DeepSeek, and Anthropic providers
- Configuration via `keira.properties` file in `.minecraft/config/` directory
- Uses Spring AI 1.0.1 framework for chat integration
- Includes embedded H2 database for conversation persistence

## Common Tasks

### Repository Structure
```
/home/runner/work/Keira/Keira/
├── README.md, README.zh-CN.md          # Project documentation
├── AGENTS.md                           # AI agent development guidelines
├── build.gradle                        # Main build configuration
├── gradle.properties                   # Version and dependency config
├── src/main/java/com/keira/            # Main mod source code
│   ├── KeiraAiMod.java                 # Main mod entry point
│   ├── ai/                             # AI integration and configuration
│   ├── chat/                           # Chat system and messaging
│   ├── command/                        # Command registration and handling
│   ├── tools/                          # AI tools for game interaction
│   ├── persistence/                    # Database and data storage
│   └── util/                           # Utility classes
├── src/client/java/com/keira/          # Client-side only code
├── src/main/resources/                 # Resources and configuration
│   ├── fabric.mod.json                 # Mod metadata
│   ├── assets/keira/lang/              # Localization files (en_us, zh_cn, etc.)
│   └── mybatis-config.xml              # Database configuration
├── .github/workflows/ci-cd.yml         # CI/CD pipeline
└── build/libs/                         # Build artifacts (after gradle build)
```

### Key Entry Points and Files
- **Main Mod Class**: `src/main/java/com/keira/KeiraAiMod.java`
- **Command Registry**: `src/main/java/com/keira/command/AiCommandRegistry.java`
- **AI Configuration**: `src/main/java/com/keira/ai/AiConfig.java`
- **Chat System**: `src/main/java/com/keira/chat/AiChatSystem.java`
- **Localization**: `src/main/resources/assets/keira/lang/en_us.json`

### Gradle Tasks Reference
### Essential tasks for development:
- `./gradlew tasks` - List all available tasks (7 seconds)
- `./gradlew clean` - Clean build artifacts (7 seconds)
- `./gradlew build` - Full build with tests (2 minutes)
- `./gradlew check` - Run all checks (8 seconds)
- `./gradlew runClient` - Launch Minecraft client with mod (30+ seconds)
- `./gradlew runServer` - Launch Minecraft server with mod (20 seconds)
- `./gradlew sourcesJar` - Generate sources JAR (8 seconds)

### Most Frequently Used Commands
```bash
# Standard development workflow (with timing)
./gradlew clean build --no-daemon     # Build everything (30 sec with cache, 2 min without)
./gradlew runServer --no-daemon       # Test server (20 sec to validate loading)

# Quick validation after changes
./gradlew check --no-daemon           # Quick compile check (8 sec)
./gradlew runServer --no-daemon       # Verify mod loads (20 sec)
```

### Build Artifacts
Successful build generates:
- `build/libs/Keira-0.1.1.jar` (~23MB) - Main mod JAR with all dependencies
- `build/libs/Keira-0.1.1-sources.jar` (~1.6MB) - Source code JAR

### Development Environment Setup Commands Reference

```bash
# Initial setup (run once)
export PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
cd /home/runner/work/Keira/Keira

# Verify environment
java -version  # Should show Java 21
./gradlew --version  # Should show Gradle 8.14.2

# Standard development workflow
./gradlew clean build --no-daemon     # Build everything (2 min)
./gradlew runServer --no-daemon       # Test server (15 sec to start)
```

## Internationalization Requirements

Following project AGENTS.md guidelines:
- **Server Language**: Always `en_us`
- **Client Language**: Respect client settings
- **AI Prompts**: Always in English
- **User Messages**: Use client language via localization keys in `assets/keira/lang/`
- **Code Comments**: Always in English

## Critical Reminders

- **NEVER CANCEL** long-running commands - builds can take 2+ minutes
- **ALWAYS** use Java 21 - other versions will fail
- **ALWAYS** use `--no-daemon` for CI/build environments to avoid hanging
- **ALWAYS** test server startup after command or AI system changes
- **NEVER** commit without successful `./gradlew clean build`
- **ALWAYS** verify mod loads by checking for "✅ Command registration callback attached" message

## Troubleshooting Common Issues

### Java Version Problems
If build fails with version errors:
```bash
# Check current Java version
java -version

# Set to Java 21 if not already
export PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### Build Failures
If build fails:
1. Ensure Java 21 is active: `java -version`
2. Clean and retry: `./gradlew clean build --no-daemon`
3. Check for dependency issues in `gradle.properties`
4. Verify no syntax errors in main mod files

### Mod Loading Issues
If mod doesn't load properly:
1. Check server logs for the three success messages (🤖, ✅, ✨)
2. Verify `fabric.mod.json` syntax is valid
3. Ensure all dependencies in `build.gradle` are correct
4. Test with minimal changes to identify problematic code

### Performance Notes
- **First build (no cache)**: ~2 minutes (downloads dependencies)
- **Clean build (with cache)**: ~30 seconds (dependencies cached)
- **Incremental builds**: ~10-20 seconds (minimal changes)
- **Development server startup**: ~20 seconds to validate mod loading