## Coding style
1. When writing code, please make sure to keep the code elegant and reusable.
2. When you are modifying existing code, if you find that some code is not elegant, unreasonable, or does not meet the reusability requirements, you should be responsible for refactoring them instead of adapting to them.
3. Do not use fully qualified class names, etc., and use the most elegant way
4. When you write code, comments should be in English
5. Logs should be hierarchical. Debug logs are used to track processes. Info level and above logs are used to prompt users with important information. Do not abuse info level and above logs.

## Fabric MOD , User Code Rule
1. When you use the fabric API, you should look at the fabric-related dependency versions in gradle.properties and choose the appropriate API
2. The server language should be en_us, and the client language should be based on the client's own settings. Internationalization should be done well.
3. Regarding the prompt words given to AI and the prompt words of the tool, in short, the text given to AI should all be kept in English, and the final output of AI to the user and the prompts sent to the user by the system should be internationalized according to the client language type.
   For example, the message returned by AI should be based on the language type of the client, which specifies the language in which the AI return message should be returned.
   The messages that the system prompts to users should be internationalized according to the existing internationalization strategy.

## Minecraft Sources (Fabric Loom)
- Source of truth: Minecraft game sources are decompiled and remapped by Fabric Loom and stored under `.gradle/loom-cache` within the project.  MC game source code under `.gradle/loom-cache/minecraftMaven/net/minecraft`
- Do not guess or write version-agnostic reflection/compat code when the proper API exists. Prefer calling the official, mapped classes and methods exposed by the current Yarn version.
- When interacting with server/client internals, call the local game functions directly (as mapped by Yarn) rather than attempting to parse files or reflect into internals. Look up the real class names and APIs in the Loom cache first.
- Code comments, identifiers, and prompts sent to AI models must be written in English. Conversations and user-facing discussion here should be in Chinese following the i18n rules above.

## AI Pull Request Creation Guidelines

When creating pull requests (whether by AI agents or developers), follow these guidelines:

### 1. Issue Requirements
- **MUST** have a corresponding issue before creating PR
- If no issue exists, create one first using the appropriate template
- Use "Closes #XXX" format in PR description to link the issue

### 2. PR Title Format
Use one of these prefixes:
- `fix:` for bug fixes
- `feat:` for new features  
- `enhance:` for improvements to existing features
- `docs:` for documentation changes
- `refactor:` for code refactoring
- `perf:` for performance improvements

Example: `feat: Add AI memory persistence for player building preferences`

### 3. PR Description Structure
```markdown
## 🔗 Related Issue
Closes #123

## 📝 What does this PR do?
[Clear, concise description of changes]

## 🧪 How was this tested?
[Brief testing summary]
- [ ] Tested in single player
- [ ] Tested in multiplayer
- [ ] Works with existing saves

## 📋 Basic Checklist
[Use the simplified checklist from PR template]
```

### 4. Code Quality Standards
- Follow existing code patterns and style
- Use proper Fabric API versions from gradle.properties
- Maintain thread safety for server operations
- Keep changes minimal and focused
- Ensure proper error handling

### 5. Internationalization (i18n)
- User-facing messages: Use client language settings
- Server/AI messages: Keep in English (en_us)
- AI prompts: Always in English for consistency
- Use proper localization keys for user messages

### 6. AI-Specific Considerations
- Document any new AI prompt templates
- Test AI integrations thoroughly
- Ensure AI responses work in different languages
- Maintain conversation memory functionality
- Validate tool integrations

### 7. Testing Requirements (when applicable)
- Single player testing for core features
- Multiplayer testing for server features
- Compatibility with existing saves
- Integration testing with other mods (if relevant)

### 8. Common Mistakes to Avoid
- Don't create PRs without corresponding issues
- Don't use reflection when proper APIs exist
- Don't mix languages in AI prompts/responses
- Don't make breaking changes without discussion
- Don't include unnecessary complexity
