#!/usr/bin/env python3
"""
AI Issue Processing Script
Processes AI-driven issues using OpenAI or DeepSeek APIs
"""

import os
import sys
import json
import re
import logging
from typing import Dict, Tuple, Optional

# Set up logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def extract_issue_content(issue_body: str) -> Dict[str, str]:
    """Extract content from AI-driven issue body"""
    content = {
        'description': '',
        'additional_context': '',
        'images': []
    }
    
    try:
        # Split by sections and extract content
        lines = issue_body.split('\n')
        current_section = None
        current_content = []
        
        for line in lines:
            line = line.strip()
            
            # Skip empty lines and markdown headers
            if not line or line.startswith('#'):
                continue
                
            # Look for the description section
            if 'Tell us what you\'re thinking' in line or 'Describe Your Idea' in line:
                if current_section and current_content:
                    content[current_section] = '\n'.join(current_content).strip()
                current_section = 'description'
                current_content = []
                continue
            
            # Look for additional context section  
            if 'Additional Context' in line:
                if current_section and current_content:
                    content[current_section] = '\n'.join(current_content).strip()
                current_section = 'additional_context'
                current_content = []
                continue
                
            # Extract markdown image links
            img_matches = re.findall(r'!\[.*?\]\((.*?)\)', line)
            if img_matches:
                content['images'].extend(img_matches)
                
            # Collect content for current section
            if current_section and line:
                current_content.append(line)
        
        # Add final section
        if current_section and current_content:
            content[current_section] = '\n'.join(current_content).strip()
            
    except Exception as e:
        logger.error(f"Error extracting issue content: {e}")
        # Fallback: use entire body as description
        content['description'] = issue_body
    
    return content

def classify_issue_type(description: str) -> Tuple[str, str, str]:
    """Classify issue type using keyword analysis"""
    
    text_lower = description.lower()
    
    # Bug indicators
    bug_keywords = ['crash', 'error', 'bug', 'broken', 'not work', 'fail', 'exception', 'wrong', 'incorrect']
    
    # Enhancement indicators  
    enhancement_keywords = ['improve', 'better', 'enhance', 'optimize', 'faster', 'easier', 'more convenient']
    
    # Feature indicators
    feature_keywords = ['add', 'new', 'feature', 'want', 'wish', 'could', 'should be able to', 'support']
    
    # Count matches
    bug_score = sum(1 for word in bug_keywords if word in text_lower)
    enhancement_score = sum(1 for word in enhancement_keywords if word in text_lower)  
    feature_score = sum(1 for word in feature_keywords if word in text_lower)
    
    # Determine type
    if bug_score > enhancement_score and bug_score > feature_score:
        return 'bug', 'p1', 'bug,needs-triage'
    elif enhancement_score > feature_score:
        return 'enhancement', 'p2', 'enhancement,needs-triage'
    else:
        return 'feature', 'p2', 'feature-request,needs-triage'

def generate_formatted_content(issue_type: str, content: Dict[str, str]) -> str:
    """Generate formatted issue content based on type"""
    
    description = content.get('description', '')
    additional_context = content.get('additional_context', '')
    images = content.get('images', [])
    
    # Add image references back
    image_section = ""
    if images:
        image_section = "\n\n## Screenshots/Images\n"
        for img in images:
            image_section += f"![Image]({img})\n"
    
    if issue_type == 'bug':
        return f"""## Bug Description
{description}

## Environment Information
Please provide:
| Info | Details |
|------|---------|
| Mod Version | Please specify |
| Minecraft Version | Please specify |
| Fabric Version | Please specify |
| AI Provider | OpenAI/DeepSeek/Claude |

## Steps to Reproduce
Please provide detailed steps to reproduce this issue.

## Expected vs Actual Behavior
- **Expected:** [Please describe expected behavior]
- **Actual:** [Please describe what actually happens]

## Additional Context
{additional_context or "Please provide any additional details, logs, or context that might help identify the issue."}{image_section}

---
*This issue was automatically formatted from an AI-driven template.*"""

    elif issue_type == 'enhancement':
        return f"""## Current Behavior
{description}

## Desired Improvement
Please describe how this could be improved.

## Why would this be better?
Please explain the benefits this enhancement would provide.

## Version Information
| Info | Details |
|------|---------|
| Mod Version | Please specify |
| Minecraft Version | Please specify |
| Fabric Version | Please specify |

## Additional Context
{additional_context or "Any additional examples or context that would help understand the enhancement."}{image_section}

---
*This issue was automatically formatted from an AI-driven template.*"""

    else:  # feature
        return f"""## Feature Description
{description}

## Problem Statement
What problem would this feature solve?

## How should it work?
Please describe how you imagine this feature working.

## Version Information
| Info | Details |
|------|---------|
| Mod Version | Please specify |
| Minecraft Version | Please specify |
| Fabric Version | Please specify |

## Additional Context
{additional_context or "Any additional examples, use cases, or technical details."}{image_section}

---
*This issue was automatically formatted from an AI-driven template.*"""

def main():
    """Main processing function"""
    
    # Get issue content from environment
    issue_body = os.environ.get('ISSUE_BODY', '')
    issue_number = os.environ.get('ISSUE_NUMBER', '')
    
    if not issue_body:
        logger.error("No issue body provided")
        sys.exit(1)
        
    logger.info(f"Processing issue #{issue_number}")
    
    # Extract content
    content = extract_issue_content(issue_body)
    logger.info(f"Extracted description: {content['description'][:100]}...")
    
    # Classify issue type
    issue_type, priority, labels = classify_issue_type(content['description'])
    logger.info(f"Classified as: {issue_type} (priority: {priority})")
    
    # Generate formatted content
    formatted_content = generate_formatted_content(issue_type, content)
    
    # Output results for GitHub Actions
    output_file = os.environ.get('GITHUB_OUTPUT', '/dev/stdout')
    with open(output_file, 'a', encoding='utf-8') as f:
        f.write(f"issue_type={issue_type}\n")
        f.write(f"priority={priority}\n") 
        f.write(f"labels={labels}\n")
        f.write(f"formatted_content<<EOF\n{formatted_content}\nEOF\n")
    
    logger.info("Processing completed successfully")

if __name__ == "__main__":
    main()