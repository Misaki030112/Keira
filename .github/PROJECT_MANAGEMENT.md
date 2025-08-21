# Ausuka.ai Project Management Setup

This directory contains the project management automation for the Ausuka.ai Minecraft mod.

## Overview

The project management system provides:

1. **Automated Issue Processing** - AI-driven issue categorization and formatting
2. **Issue-PR Relationship Enforcement** - Strict 1:1 relationship between issues and pull requests
3. **Project Board Integration** - Automatic synchronization with GitHub Projects
4. **Weekly Management Cycles** - Automated project health monitoring and archival

## Components

### Issue Templates

- **Bug Report** (`bug_report.yml`) - Structured template for bug reports
- **Feature Request** (`feature_request.yml`) - Template for new feature suggestions  
- **Enhancement** (`enhancement.yml`) - Template for improvements to existing features
- **AI-Driven** (`ai_driven.yml`) - Natural language template processed by AI

### PR Template

- **Standard PR Template** (`pull_request_template.md`) - Comprehensive template for all PRs with AI development guidelines

### Automation Workflows

- **AI Issue Processing** (`ai-issue-processing.yml`) - Basic AI processing workflow
- **Enhanced AI Processing** (`enhanced-ai-processing.yml`) - Advanced AI analysis using OpenAI/DeepSeek APIs
- **Issue-PR Enforcement** (`issue-pr-enforcement.yml`) - Enforces 1:1 issue-PR relationships
- **Project Management** (`project-management.yml`) - Weekly cycles, metrics, and automation

## Setup Instructions

### 1. GitHub Project Board Setup

Create a new GitHub Project with the following structure:

**Columns:**
- 📥 **Incoming** - New issues and PRs awaiting triage
- 🔍 **In Review** - Issues being evaluated and discussed
- 🏗️ **In Progress** - Issues with associated PRs being developed
- ✅ **Done** - Completed issues and merged PRs
- 📦 **Archived** - Items archived during weekly cycles

**Custom Fields:**
- **Priority** (Select): High, Medium, Low
- **Area** (Multi-select): AI, Admin, Config, Performance, I18n, UI, Tools
- **Effort** (Number): Story points or complexity estimate
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