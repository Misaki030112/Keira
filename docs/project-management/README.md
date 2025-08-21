# Project Management Documentation

Simple project management setup for the Ausuka.ai mod repository.

## Issue Templates

- **🤖 AI-Driven** - Simple template where users describe their ideas in natural language
- **🐛 Bug Report** - Standard bug reporting with version info
- **🔧 Enhancement** - Improvements to existing features  
- **✨ Feature Request** - New feature suggestions
- **❓ Q&A** - Questions about the mod

## Automation

### AI Processing
- Automatically processes AI-driven issues
- Converts natural language to structured formats
- Uses Python for reliable text processing

### Issue-PR Linking
- Enforces "Closes #XXX" format in PRs
- Maintains 1:1 relationship between issues and PRs

### Weekly Management
- Archives closed issues weekly
- Marks issues stale after 6 months
- Generates project health reports

## Labels

### Types
- `bug` - Something isn't working
- `enhancement` - Improvement to existing functionality  
- `feature-request` - New functionality request
- `question` - Questions needing answers

### Priority (p0-p4)
- `p0` - Critical (security, data loss)
- `p1` - High priority (major features/bugs)
- `p2` - Medium priority (normal work)
- `p3` - Low priority (nice to have)
- `p4` - Lowest (future considerations)

### Status
- `needs-triage` - Needs initial review
- `needs-info` - Waiting for more information
- `in-progress` - Currently being worked on
- `blocked` - Blocked by dependencies
- `stale` - No recent activity

## For Contributors

1. **Creating Issues**: Use the AI-driven template - just describe what you're thinking
2. **Creating PRs**: Must link to an issue using "Closes #XXX" format
3. **Questions**: Use the Q&A template for help

## For AI Agents

See `AGENTS.md` for comprehensive PR creation guidelines including:
- Required issue linking
- Title formats (fix:, feat:, enhance:, etc.)
- Testing requirements
- i18n considerations

## Examples

### Good Issue Title
- `[AI] The mod should remember my building preferences`
- `[Bug] AI crashes when teleporting to saved location`
- `[Feature] Add inventory sorting command`

### Good PR Title  
- `fix: Resolve AI crash during teleportation (Closes #123)`
- `feat: Add inventory sorting command (Closes #124)`
- `enhance: Improve AI response speed (Closes #125)`
- **Sprint** (Text): Current sprint/milestone identifier

### 2. Required Secrets

Configure the following repository secrets:

```
OPENAI_API_KEY=your_openai_api_key        # For AI processing (optional)
DEEPSEEK_API_KEY=your_deepseek_api_key    # Alternative to OpenAI (optional)
```

**Note:** AI processing will fall back to rule-based classification if no API keys are provided.

### 3. Project URL Configuration

Update the project URL in `project-management.yml`:

```yaml
project-url: https://github.com/users/YOUR_USERNAME/projects/PROJECT_NUMBER
```

### 4. Label Setup

Ensure the following labels exist in your repository:

**Type Labels:**
- `bug` (red)
- `enhancement` (blue) 
- `feature-request` (green)

**Priority Labels:**
- `priority:high` (red)
- `priority:medium` (orange)
- `priority:low` (yellow)

**Area Labels:**
- `area:ai` (purple)
- `area:admin` (brown)
- `area:config` (gray)
- `area:performance` (orange)
- `area:i18n` (blue)
- `area:ui` (green)
- `area:tools` (cyan)

**Status Labels:**
- `needs-triage` (yellow)
- `needs-review` (blue)
- `needs-issue-link` (red)
- `ai-processing` (purple)
- `ai-processing-error` (red)
- `stale` (gray)
- `keep-open` (green)

## Usage

### For Users

1. **Creating Issues:**
   - Use specific templates for known issue types
   - Use the AI-driven template for natural language descriptions
   - Provide as much detail as possible

2. **AI-Driven Issues:**
   - Simply describe your idea in plain language
   - AI will categorize and format automatically
   - Review the AI-generated content and add details if needed

### For Developers

1. **Creating PRs:**
   - Always reference exactly one issue using "Closes #XXX"
   - Follow the PR template checklist
   - Ensure all required fields are completed

2. **Issue-PR Workflow:**
   - Create issue first (or identify existing issue)
   - Create PR that references the issue
   - Automated checks will verify the 1:1 relationship
   - Merge PR to automatically close the issue

### For Maintainers

1. **Weekly Cycles:**
   - Automated weekly summaries are generated
   - Completed items are tracked and archived
   - Project metrics are calculated automatically

2. **Manual Intervention:**
   - Use `workflow_dispatch` to trigger specific actions
   - Review AI-processed issues for accuracy
   - Manage stale issues and PRs

## Customization

### AI Processing

The AI processing can be customized by modifying the prompt in `enhanced-ai-processing.yml`. The system supports:

- Custom classification rules
- Different AI providers
- Fallback to rule-based processing
- Custom label assignment logic

### Project Board Automation

Project board integration can be customized by:

- Modifying the GraphQL queries for project updates
- Adding custom field updates
- Implementing different column structures
- Creating custom automation rules

### Weekly Cycles

Weekly management cycles can be adjusted by:

- Changing the cron schedule
- Modifying the metrics calculation
- Customizing the archival logic
- Adding custom reporting features

## Monitoring

The automation provides several monitoring capabilities:

1. **GitHub Actions Logs** - Detailed logs of all automation activities
2. **Job Summaries** - Weekly metrics and summaries in Actions UI
3. **Issue Comments** - Status updates and processing results
4. **Labels** - Visual indicators of automation status

## Troubleshooting

### Common Issues

1. **AI Processing Failures:**
   - Check API key configuration
   - Verify API quotas and limits
   - Review error messages in Actions logs

2. **Project Board Sync Issues:**
   - Verify project URL configuration
   - Check project permissions
   - Ensure project board structure matches expectations

3. **Issue-PR Enforcement:**
   - Verify issue references in PR descriptions
   - Check for conflicting PRs
   - Ensure referenced issues exist and are open

### Support

For issues with the project management automation:

1. Check the GitHub Actions logs for detailed error information
2. Create an issue using the bug report template
3. Include relevant log excerpts and error messages
4. Tag with `area:project-management` label

---

*This project management system is designed to scale with the project and can be adapted to different workflows and requirements.*