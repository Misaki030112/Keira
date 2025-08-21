#!/bin/bash

# 🧪 Project Management Testing Script
# This script helps test the automated project management workflows

set -e

echo "🎯 Ausuka.ai Project Management Testing"
echo "======================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test functions
test_issue_templates() {
    echo -e "${BLUE}📋 Testing Issue Templates...${NC}"
    
    if [ -f ".github/ISSUE_TEMPLATE/bug_report.yml" ]; then
        echo -e "${GREEN}✅ Bug report template exists${NC}"
    else
        echo -e "${RED}❌ Bug report template missing${NC}"
    fi
    
    if [ -f ".github/ISSUE_TEMPLATE/feature_request.yml" ]; then
        echo -e "${GREEN}✅ Feature request template exists${NC}"
    else
        echo -e "${RED}❌ Feature request template missing${NC}"
    fi
    
    if [ -f ".github/ISSUE_TEMPLATE/enhancement.yml" ]; then
        echo -e "${GREEN}✅ Enhancement template exists${NC}"
    else
        echo -e "${RED}❌ Enhancement template missing${NC}"
    fi
    
    if [ -f ".github/ISSUE_TEMPLATE/ai_driven.yml" ]; then
        echo -e "${GREEN}✅ AI-driven template exists${NC}"
    else
        echo -e "${RED}❌ AI-driven template missing${NC}"
    fi
    
    if [ -f ".github/ISSUE_TEMPLATE/config.yml" ]; then
        echo -e "${GREEN}✅ Issue template config exists${NC}"
    else
        echo -e "${RED}❌ Issue template config missing${NC}"
    fi
}

test_pr_template() {
    echo -e "${BLUE}📝 Testing PR Template...${NC}"
    
    if [ -f ".github/pull_request_template.md" ]; then
        echo -e "${GREEN}✅ PR template exists${NC}"
    else
        echo -e "${RED}❌ PR template missing${NC}"
    fi
}

test_workflows() {
    echo -e "${BLUE}⚙️ Testing Workflows...${NC}"
    
    workflows=(
        "ai-issue-processing.yml"
        "enhanced-ai-processing.yml" 
        "issue-pr-enforcement.yml"
        "project-management.yml"
        "setup-labels.yml"
    )
    
    for workflow in "${workflows[@]}"; do
        if [ -f ".github/workflows/$workflow" ]; then
            echo -e "${GREEN}✅ $workflow exists${NC}"
        else
            echo -e "${RED}❌ $workflow missing${NC}"
        fi
    done
}

validate_yaml_syntax() {
    echo -e "${BLUE}🔍 Validating YAML Syntax...${NC}"
    
    # Check if yamllint is available
    if command -v yamllint &> /dev/null; then
        echo "Using yamllint for validation..."
        
        # Validate issue templates
        for template in .github/ISSUE_TEMPLATE/*.yml; do
            if yamllint "$template" &> /dev/null; then
                echo -e "${GREEN}✅ $(basename "$template") is valid${NC}"
            else
                echo -e "${RED}❌ $(basename "$template") has YAML errors${NC}"
                yamllint "$template"
            fi
        done
        
        # Validate workflows
        for workflow in .github/workflows/*.yml; do
            if yamllint "$workflow" &> /dev/null; then
                echo -e "${GREEN}✅ $(basename "$workflow") is valid${NC}"
            else
                echo -e "${RED}❌ $(basename "$workflow") has YAML errors${NC}"
                yamllint "$workflow"
            fi
        done
    else
        echo -e "${YELLOW}⚠️ yamllint not available, skipping YAML validation${NC}"
        echo "To install: pip install yamllint"
    fi
}

check_project_setup() {
    echo -e "${BLUE}🏗️ Checking Project Setup...${NC}"
    
    # Check if project URL is configured
    if grep -q "https://github.com/users/Misaki030112/projects/1" .github/workflows/project-management.yml; then
        echo -e "${YELLOW}⚠️ Project URL needs to be updated in project-management.yml${NC}"
        echo "   Update line with your actual project URL"
    else
        echo -e "${GREEN}✅ Project URL appears to be configured${NC}"
    fi
    
    # Check for documentation
    if [ -f ".github/PROJECT_MANAGEMENT.md" ]; then
        echo -e "${GREEN}✅ Project management documentation exists${NC}"
    else
        echo -e "${RED}❌ Project management documentation missing${NC}"
    fi
}

check_secrets() {
    echo -e "${BLUE}🔐 Secret Configuration Guide...${NC}"
    echo -e "${YELLOW}ℹ️ To enable AI processing, configure these secrets:${NC}"
    echo "   Repository Settings → Secrets and variables → Actions"
    echo "   - OPENAI_API_KEY (for OpenAI GPT-4 processing)"
    echo "   - DEEPSEEK_API_KEY (alternative AI provider)"
    echo ""
    echo "   Note: AI processing will fall back to rule-based if no keys provided"
}

generate_test_commands() {
    echo -e "${BLUE}🧪 Test Commands...${NC}"
    echo "Run these GitHub CLI commands to test workflows:"
    echo ""
    echo "# Test label setup"
    echo "gh workflow run setup-labels.yml -f action=create"
    echo ""
    echo "# Test project management (manual trigger)"
    echo "gh workflow run project-management.yml -f action=sync"
    echo ""
    echo "# Create a test issue to test AI processing"
    echo "gh issue create --title '[AI] Test AI processing' --body 'Test AI processing with natural language'"
    echo ""
    echo "# Create a test PR to test enforcement"
    echo "gh pr create --title 'Test PR' --body 'Closes #1' --head feature-branch --base main"
}

print_next_steps() {
    echo -e "${BLUE}🚀 Next Steps...${NC}"
    echo "1. Update project URL in .github/workflows/project-management.yml"
    echo "2. Create GitHub Project board at https://github.com/Misaki030112/projects"
    echo "3. Run: gh workflow run setup-labels.yml -f action=create"
    echo "4. Configure AI API keys (optional but recommended)"
    echo "5. Test with a sample AI-driven issue"
    echo "6. Create PR linking to the test issue"
    echo ""
    echo -e "${GREEN}📖 Read PROJECT_SETUP_GUIDE.md for detailed instructions${NC}"
}

# Main execution
main() {
    test_issue_templates
    echo ""
    test_pr_template
    echo ""
    test_workflows
    echo ""
    validate_yaml_syntax
    echo ""
    check_project_setup
    echo ""
    check_secrets
    echo ""
    generate_test_commands
    echo ""
    print_next_steps
}

# Run if executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi