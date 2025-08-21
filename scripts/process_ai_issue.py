#!/usr/bin/env python3
"""
AI Issue Processing Script
Processes AI-driven issues using proper AI libraries and semantic analysis
"""

import os
import sys
import json
import re
import logging
import yaml
from typing import Dict, Tuple, Optional
from pathlib import Path

# Set up logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Try to import AI libraries - expose failures immediately
try:
    import openai
except ImportError as e:
    logger.error(f"Failed to import openai: {e}")
    openai = None

try:
    import anthropic
except ImportError as e:
    logger.error(f"Failed to import anthropic: {e}")
    anthropic = None

try:
    from groq import Groq
except ImportError as e:
    logger.error(f"Failed to import groq: {e}")
    Groq = None

def load_issue_templates() -> Dict[str, str]:
    """Load issue templates from .github/ISSUE_TEMPLATE/ directory"""
    templates = {}
    template_dir = Path(__file__).parent.parent / '.github' / 'ISSUE_TEMPLATE'
    
    try:
        for template_file in template_dir.glob('*.yml'):
            template_name = template_file.stem
            with open(template_file, 'r', encoding='utf-8') as f:
                template_content = yaml.safe_load(f)
                templates[template_name] = template_content
                logger.info(f"Loaded template: {template_name}")
    except Exception as e:
        logger.error(f"Error loading templates: {e}")
    
    return templates

def extract_issue_content(issue_body: str) -> Dict[str, str]:
    """Extract content from AI-driven issue body"""
    content = {
        'description': '',
        'additional_context': '',
        'images': []
    }
    
    try:
        # Extract markdown image links first
        img_matches = re.findall(r'!\[.*?\]\((.*?)\)', issue_body)
        if img_matches:
            content['images'].extend(img_matches)
        
        # Clean the body and extract main content
        lines = issue_body.split('\n')
        description_lines = []
        in_description = False
        
        for line in lines:
            line = line.strip()
            
            # Skip empty lines, headers, and metadata
            if not line or line.startswith('#') or line.startswith('**Note:**'):
                continue
                
            # Look for the main content area
            if 'Tell us what you\'re thinking' in line:
                in_description = True
                continue
            elif line.startswith('**') and in_description:
                # End of description section
                break
            elif in_description and line:
                description_lines.append(line)
        
        # If no structured content found, use entire body as description
        if not description_lines:
            # Remove image markdown and clean up
            cleaned_body = re.sub(r'!\[.*?\]\(.*?\)', '', issue_body)
            cleaned_body = re.sub(r'\*\*Note:\*\*.*', '', cleaned_body)
            content['description'] = cleaned_body.strip()
        else:
            content['description'] = '\n'.join(description_lines).strip()
            
    except Exception as e:
        logger.error(f"Error extracting issue content: {e}")
        # Fallback: use entire body as description
        content['description'] = issue_body
    
    return content

def classify_issue_with_ai(description: str) -> Tuple[str, str, str]:
    """Classify issue type using AI semantic analysis"""
    
    # System prompt for AI classification
    system_prompt = """You are an AI assistant that helps classify GitHub issues for a Minecraft mod called Ausuka.ai.

Your task is to analyze user descriptions and classify them into one of three categories:
1. "bug" - Issues where something is broken, not working, crashes, errors, or behaves incorrectly
2. "enhancement" - Requests to improve or optimize existing features to make them better/faster/easier
3. "feature" - Requests for completely new functionality that doesn't exist yet

You must respond with exactly this JSON format:
{
    "type": "bug|enhancement|feature",
    "priority": "p0|p1|p2|p3|p4",
    "confidence": 0.0-1.0,
    "reasoning": "brief explanation"
}

Priority levels:
- p0: Critical issues (crashes, data loss, security)
- p1: High priority (major bugs, important features)
- p2: Medium priority (minor bugs, useful enhancements)
- p3: Low priority (nice-to-have improvements)
- p4: Lowest priority (cosmetic changes, very minor issues)"""

    user_prompt = f"""Please classify this issue description:

"{description}"

Respond with the JSON classification."""

    try:
        # Try different AI providers in order of preference
        result = None
        
        # Try OpenAI first
        if openai and os.environ.get('OPENAI_API_KEY'):
            try:
                client = openai.OpenAI(api_key=os.environ.get('OPENAI_API_KEY'))
                response = client.chat.completions.create(
                    model="gpt-4o",
                    messages=[
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt}
                    ],
                    temperature=0.1,
                    max_tokens=200
                )
                result = response.choices[0].message.content
                logger.info("Used OpenAI for classification")
            except Exception as e:
                logger.warning(f"OpenAI failed: {e}")
        
        # Try DeepSeek API (compatible with OpenAI format)
        if not result and os.environ.get('DEEPSEEK_API_KEY'):
            try:
                import requests
                response = requests.post(
                    "https://api.deepseek.com/chat/completions",
                    headers={
                        "Authorization": f"Bearer {os.environ.get('DEEPSEEK_API_KEY')}",
                        "Content-Type": "application/json"
                    },
                    json={
                        "model": "deepseek-chat",
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": user_prompt}
                        ],
                        "temperature": 0.1,
                        "max_tokens": 200
                    },
                    timeout=30
                )
                if response.status_code == 200:
                    result = response.json()["choices"][0]["message"]["content"]
                    logger.info("Used DeepSeek for classification")
            except Exception as e:
                logger.warning(f"DeepSeek failed: {e}")
        
        # Try Anthropic Claude
        if not result and anthropic and os.environ.get('ANTHROPIC_API_KEY'):
            try:
                client = anthropic.Anthropic(api_key=os.environ.get('ANTHROPIC_API_KEY'))
                response = client.messages.create(
                    model="claude-3-haiku-20240307",
                    max_tokens=200,
                    temperature=0.1,
                    system=system_prompt,
                    messages=[{"role": "user", "content": user_prompt}]
                )
                result = response.content[0].text
                logger.info("Used Anthropic Claude for classification")
            except Exception as e:
                logger.warning(f"Anthropic failed: {e}")
        
        # Parse AI response
        if result:
            try:
                # Extract JSON from response
                import json
                json_match = re.search(r'\{.*\}', result, re.DOTALL)
                if json_match:
                    classification = json.loads(json_match.group())
                    issue_type = classification.get('type', 'feature')
                    priority = classification.get('priority', 'p2')
                    confidence = classification.get('confidence', 0.5)
                    reasoning = classification.get('reasoning', '')
                    
                    logger.info(f"AI Classification: {issue_type} ({priority}) - confidence: {confidence}")
                    logger.info(f"Reasoning: {reasoning}")
                    
                    # Map to labels
                    label_map = {
                        'bug': 'bug,needs-triage',
                        'enhancement': 'enhancement,needs-triage', 
                        'feature': 'feature-request,needs-triage'
                    }
                    
                    return issue_type, priority, label_map.get(issue_type, 'needs-triage')
            except Exception as e:
                logger.error(f"Error parsing AI response: {e}")
                
    except Exception as e:
        logger.error(f"AI classification failed: {e}")
    
    # Fallback to simple heuristic
    logger.warning("Falling back to keyword-based classification")
    return classify_issue_fallback(description)

def classify_issue_fallback(description: str) -> Tuple[str, str, str]:
    """Fallback classification using keyword analysis when AI fails"""
    
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

def generate_formatted_content(issue_type: str, content: Dict[str, str], templates: Dict[str, str]) -> str:
    """Generate formatted issue content based on actual issue templates"""
    
    description = content.get('description', '')
    additional_context = content.get('additional_context', '')
    images = content.get('images', [])
    
    # Add image references back
    image_section = ""
    if images:
        image_section = "\n\n## Screenshots/Images\n"
        for img in images:
            image_section += f"![Image]({img})\n"
    
    # Get the appropriate template
    template_map = {
        'bug': 'bug_report',
        'enhancement': 'enhancement',
        'feature': 'feature_request'
    }
    
    template_key = template_map.get(issue_type, 'feature_request')
    template = templates.get(template_key, {})
    
    if issue_type == 'bug':
        return f"""## Bug Description
{description}

## Version Information
| Info | Details |
|------|---------|
| Mod Version | Please specify (e.g., 1.0.0) |
| Minecraft Version | Please specify (e.g., 1.21.4) |
| Fabric Version | Please specify (e.g., 0.16.0) |
| AI Provider | OpenAI/DeepSeek/Claude |

## Steps to Reproduce
Please provide detailed steps to reproduce this issue:
1. 
2. 
3. 

## Expected vs Actual Behavior
- **Expected:** [Describe what should happen]
- **Actual:** [Describe what actually happens]

## Environment
Please check all that apply:
- [ ] Single player
- [ ] Multiplayer (client)
- [ ] Dedicated server
- [ ] Using other mods

## AI Configuration
- [ ] Using OpenAI API
- [ ] Using DeepSeek API  
- [ ] Using Claude API
- [ ] AI features working normally before this issue

## Logs
```
[Please paste relevant logs from latest.log or crash reports here]
```

## Additional Context
{additional_context or "Add any other context about the problem here."}{image_section}

---
*This issue was automatically formatted from an AI-driven template using semantic analysis.*"""

    elif issue_type == 'enhancement':
        return f"""## Current Behavior
{description}

## Version Information
| Info | Details |
|------|---------|
| Mod Version | Please specify |
| Minecraft Version | Please specify |
| Fabric Version | Please specify |

## What would be better?
[Describe the improvement you're suggesting]

## Why would this be better?
[Explain what benefits this would provide, such as:]
- Makes it easier to...
- Saves time because...
- More intuitive because...

## Examples
[Give specific examples of when this would help:]
1. When I'm building and need to...
2. During gameplay it would help if...

## Technical Details (Optional)
{additional_context or "[Only fill this if you have technical knowledge about implementation]"}{image_section}

---
*This issue was automatically formatted from an AI-driven template using semantic analysis.*"""

    else:  # feature
        return f"""## Feature Description
{description}

## Version Information
| Info | Details |
|------|---------|
| Mod Version | Please specify |
| Minecraft Version | Please specify |
| Fabric Version | Please specify |

## What problem would this solve?
[Explain why you need this feature]

## How should it work?
[Describe how you imagine this feature working]

## Examples
[Give specific examples of how you'd use this:]
Example 1: When I'm mining, I could ask...
Example 2: During building, it would help if...

## Technical Details (Optional)
{additional_context or "[Only fill this if you have technical suggestions or implementation ideas]"}{image_section}

---
*This issue was automatically formatted from an AI-driven template using semantic analysis.*"""

def main():
    """Main processing function"""
    
    # Get issue content from environment
    issue_body = os.environ.get('ISSUE_BODY', '')
    issue_number = os.environ.get('ISSUE_NUMBER', '')
    
    if not issue_body:
        logger.error("No issue body provided")
        sys.exit(1)
        
    logger.info(f"Processing issue #{issue_number}")
    
    # Load issue templates
    templates = load_issue_templates()
    
    # Extract content
    content = extract_issue_content(issue_body)
    logger.info(f"Extracted description: {content['description'][:100]}...")
    
    # Classify issue type using AI
    issue_type, priority, labels = classify_issue_with_ai(content['description'])
    logger.info(f"Classified as: {issue_type} (priority: {priority})")
    
    # Generate formatted content based on templates
    formatted_content = generate_formatted_content(issue_type, content, templates)
    
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