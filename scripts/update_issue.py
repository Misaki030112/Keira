#!/usr/bin/env python3
"""
GitHub Issue Update Script
Updates GitHub issues using Python instead of shell scripts
"""

import os
import sys
import json
import logging
import requests
from typing import Dict, List

# Set up logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class GitHubIssueUpdater:
    def __init__(self, token: str, owner: str, repo: str):
        self.token = token
        self.owner = owner
        self.repo = repo
        self.headers = {
            'Authorization': f'Bearer {token}',
            'Accept': 'application/vnd.github.v3+json',
            'Content-Type': 'application/json'
        }
        self.base_url = f'https://api.github.com/repos/{owner}/{repo}'

    def parse_github_output(self, output_content: str) -> Dict[str, str]:
        """Parse GitHub Actions output format"""
        data = {}
        lines = output_content.split('\n')
        i = 0
        
        while i < len(lines):
            line = lines[i].strip()
            
            if '=' in line and not line.startswith('formatted_content'):
                key, value = line.split('=', 1)
                data[key] = value
            elif line.startswith('formatted_content<<EOF'):
                # Multi-line content
                content_lines = []
                i += 1
                while i < len(lines) and lines[i].strip() != 'EOF':
                    content_lines.append(lines[i])
                    i += 1
                data['formatted_content'] = '\n'.join(content_lines)
            
            i += 1
        
        return data

    def update_issue(self, issue_number: int, title: str = None, body: str = None) -> bool:
        """Update issue title and/or body"""
        try:
            url = f'{self.base_url}/issues/{issue_number}'
            update_data = {}
            
            if title:
                update_data['title'] = title
            if body:
                update_data['body'] = body
            
            response = requests.patch(url, headers=self.headers, json=update_data, timeout=30)
            response.raise_for_status()
            
            logger.info(f"Successfully updated issue #{issue_number}")
            return True
            
        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to update issue #{issue_number}: {e}")
            return False

    def remove_label(self, issue_number: int, label: str) -> bool:
        """Remove a label from an issue"""
        try:
            url = f'{self.base_url}/issues/{issue_number}/labels/{label}'
            response = requests.delete(url, headers=self.headers, timeout=30)
            
            if response.status_code == 404:
                logger.info(f"Label '{label}' not found on issue #{issue_number}")
                return True
            
            response.raise_for_status()
            logger.info(f"Successfully removed label '{label}' from issue #{issue_number}")
            return True
            
        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to remove label '{label}' from issue #{issue_number}: {e}")
            return False

    def add_labels(self, issue_number: int, labels: List[str]) -> bool:
        """Add labels to an issue"""
        try:
            url = f'{self.base_url}/issues/{issue_number}/labels'
            response = requests.post(url, headers=self.headers, json=labels, timeout=30)
            response.raise_for_status()
            
            logger.info(f"Successfully added labels {labels} to issue #{issue_number}")
            return True
            
        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to add labels {labels} to issue #{issue_number}: {e}")
            return False

    def create_comment(self, issue_number: int, body: str) -> bool:
        """Create a comment on an issue"""
        try:
            url = f'{self.base_url}/issues/{issue_number}/comments'
            response = requests.post(url, headers=self.headers, json={'body': body}, timeout=30)
            response.raise_for_status()
            
            logger.info(f"Successfully created comment on issue #{issue_number}")
            return True
            
        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to create comment on issue #{issue_number}: {e}")
            return False

def main():
    """Main function"""
    try:
        # Get environment variables
        token = os.environ.get('GITHUB_TOKEN')
        repo_full = os.environ.get('GITHUB_REPOSITORY', '')
        issue_number = int(os.environ.get('ISSUE_NUMBER', '0'))
        original_title = os.environ.get('ISSUE_TITLE', '')
        
        if not all([token, repo_full, issue_number]):
            logger.error("Missing required environment variables")
            sys.exit(1)
        
        # Parse repository
        owner, repo = repo_full.split('/')
        
        # Initialize updater
        updater = GitHubIssueUpdater(token, owner, repo)
        
        # Read processing output
        output_file = os.environ.get('GITHUB_OUTPUT', '')
        if not output_file or not os.path.exists(output_file):
            logger.error("No GitHub output file found")
            sys.exit(1)
        
        with open(output_file, 'r', encoding='utf-8') as f:
            output_content = f.read()
        
        # Parse output
        data = updater.parse_github_output(output_content)
        
        issue_type = data.get('issue_type', 'feature')
        priority = data.get('priority', 'p2')
        labels = data.get('labels', '').split(',') if data.get('labels') else []
        formatted_content = data.get('formatted_content', '')
        
        logger.info(f"Processing data: type={issue_type}, priority={priority}, labels={labels}")
        
        # Update issue title
        new_title = original_title.replace('[AI]', f'[{issue_type.capitalize()}]')
        success = updater.update_issue(issue_number, title=new_title, body=formatted_content)
        
        if not success:
            sys.exit(1)
        
        # Remove ai-processing label
        updater.remove_label(issue_number, 'ai-processing')
        
        # Add new labels including priority
        final_labels = [label.strip() for label in labels if label.strip()] + [priority]
        updater.add_labels(issue_number, final_labels)
        
        # Create processing comment
        comment_body = f"""🤖 **AI Processing Complete**

Your issue has been automatically processed and formatted as a **{issue_type}** request.

**What happened:**
- ✅ Content analyzed using AI semantic analysis
- ✅ Issue formatted using appropriate template
- ✅ Labels applied: {', '.join(final_labels)}
- ✅ Priority assigned: {priority}

Please review the formatted content above and add any missing details.

*Processed by AI automation system using semantic analysis* 🚀"""
        
        updater.create_comment(issue_number, comment_body)
        
        logger.info("Issue processing completed successfully")
        
    except Exception as e:
        logger.error(f"Processing failed: {e}")
        
        # Try to create error comment
        try:
            if 'updater' in locals() and issue_number:
                error_comment = f"""⚠️ **AI Processing Failed**

There was an error processing your AI-driven issue.

**Error:** {str(e)}

**Next steps:**
- Please manually format your issue using one of our standard templates
- Or wait for manual review from the development team

The error has been logged and will be investigated."""
                
                updater.create_comment(issue_number, error_comment)
                updater.add_labels(issue_number, ['ai-processing-error', 'needs-triage'])
        except:
            pass
        
        sys.exit(1)

if __name__ == "__main__":
    main()