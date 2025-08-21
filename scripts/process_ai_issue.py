#!/usr/bin/env python3
"""
AI Issue Processing Script
Processes AI-driven issues via semantic analysis with strict fail-fast behavior.

Design goals:
- No keyword/rule-based fallback. If AI cannot classify, fail immediately.
- One set of dependencies installed; provider selection via environment variables.
- Clean, minimal, reusable formatting for Bug/Enhancement/Feature.
- Robust JSON parsing/validation for AI responses.
"""

import os
import sys
import json
import re
import logging
from typing import Dict, Tuple, Any, List
from pathlib import Path
import yaml

# Set up logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

def load_issue_templates() -> Dict[str, Dict[str, Any]]:
    """Load issue templates from .github/ISSUE_TEMPLATE/ directory.

    Currently only used to detect presence/types; body formatting is generated here to keep
    consistent, minimal, and reusable structure.
    """
    templates: Dict[str, Dict] = {}
    template_dir = Path(__file__).parent.parent / '.github' / 'ISSUE_TEMPLATE'

    if not template_dir.exists():
        logger.debug("ISSUE_TEMPLATE directory not found; proceeding without it")
        return templates

    for template_file in template_dir.glob('*.yml'):
        try:
            with open(template_file, 'r', encoding='utf-8') as f:
                templates[template_file.stem] = yaml.safe_load(f)
        except Exception as e:
            logger.debug(f"Skip template {template_file.name}: {e}")
    return templates


def _normalize_fields(template: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Extract normalized field schema from a GitHub Issue Form template YAML.

    Each field contains: key (stable id), label, type, options (if any).
    Markdown blocks are included with type 'markdown' and label as heading text.
    """
    fields: List[Dict[str, Any]] = []
    body = template.get('body') or []
    for item in body:
        t = item.get('type')
        if t == 'markdown':
            fields.append({
                'type': 'markdown',
                'key': item.get('id') or f"md_{len(fields)}",
                'label': item.get('attributes', {}).get('value', '')
            })
            continue
        attrs = item.get('attributes', {})
        label = attrs.get('label') or t
        key = item.get('id') or re.sub(r'[^a-z0-9]+', '_', label.lower()).strip('_') or f"f_{len(fields)}"
        entry = {
            'type': t,
            'key': key,
            'label': label,
            'required': bool((item.get('validations') or {}).get('required', False)),
        }
        if t in {'dropdown', 'checkboxes'}:
            opts = attrs.get('options') or []
            entry['options'] = [o.get('label', '') for o in opts if isinstance(o, dict)]
        fields.append(entry)
    return fields


def _select_template(templates: Dict[str, Dict[str, Any]], issue_type: str) -> Tuple[str, Dict[str, Any]]:
    """Pick a template by issue_type using common filenames; fallback to first matching name."""
    mapping = {
        'bug': ['bug_report', 'bug-report', 'bug'],
        'enhancement': ['enhancement', 'improvement'],
        'feature': ['feature_request', 'feature-request', 'feature'],
        'question': ['question', 'q&a', 'qa'],
    }
    candidates = mapping.get(issue_type, [])
    for key in candidates:
        if key in templates:
            return key, templates[key]
    # fallback by fuzzy match on template name field
    for k, v in templates.items():
        name = str(v.get('name', '')).lower()
        if issue_type in name:
            return k, v
    # last resort: any
    for k, v in templates.items():
        return k, v
    raise RuntimeError('No issue templates found')

def extract_issue_content(issue_body: str) -> Dict[str, str]:
    """Extract content from AI-driven issue body.

    Strategy: keep it simple and robust — strip image markdown, trim whitespace.
    Do not rely on template-specific markers.
    """
    images = re.findall(r'!\[[^\]]*\]\(([^)]+)\)', issue_body or '')
    cleaned = re.sub(r'!\[[^\]]*\]\([^)]+\)', '', issue_body or '').strip()
    return {
        'description': cleaned,
        'images': images,
    }

def _select_single_provider() -> str:
    """Select exactly one configured provider. No fallbacks allowed.

    Returns provider key: 'openai' | 'deepseek' | 'anthropic'.
    Fails if none or more than one is configured, or if required package is missing.
    """
    configured = []
    if os.environ.get('OPENAI_API_KEY'):
        configured.append('openai')
    if os.environ.get('DEEPSEEK_API_KEY'):
        configured.append('deepseek')
    if os.environ.get('ANTHROPIC_API_KEY'):
        configured.append('anthropic')

    if len(configured) == 0:
        logger.error("No AI provider configured. Set exactly one of OPENAI_API_KEY, DEEPSEEK_API_KEY, or ANTHROPIC_API_KEY.")
        sys.exit(2)
    if len(configured) > 1:
        logger.error(f"Multiple AI providers configured ({configured}). Configure exactly one.")
        sys.exit(2)

    provider = configured[0]
    try:
        if provider in ('openai', 'deepseek'):
            import openai  # noqa: F401
        elif provider == 'anthropic':
            import anthropic  # noqa: F401
    except ImportError as e:
        logger.error(f"Provider '{provider}' configured but required package missing: {e}")
        sys.exit(2)
    return provider


def classify_issue_with_ai(description: str, original_title: str, labels: List[str], templates: Dict[str, Dict[str, Any]]) -> Tuple[str, str, str, str, Dict[str, Any]]:
    """Classify the issue and request structured field values aligned to repository templates.

    Returns: (issue_type, priority, labels_csv, suggested_title, structured)
    where structured = { type, priority, confidence, title, template_key, fields: {key: value} }
    """
    
    # System prompt for AI classification
    system_prompt = (
        "You are a senior triage assistant for a Minecraft Fabric mod repository. "
        "Analyze the provided issue title and description. Classify it and generate a fully formatted issue body. "
        "Always output strict JSON only (no markdown fences, no commentary)."
    )

    # Build a compact schema from repository templates for the model
    schema: Dict[str, Any] = {}
    for key, tpl in templates.items():
        schema[key] = {
            'name': tpl.get('name', key),
            'fields': _normalize_fields(tpl),
        }

    def _build_system_prompt() -> str:
        return (
            "You are a senior triage assistant for a Minecraft Fabric mod repository. "
            "Classify issues and map user content onto the repository's GitHub Issue Form templates.\n"
            "Rules:\n"
            "- Respond with STRICT JSON only (no markdown fences, no commentary).\n"
            "- Do NOT invent facts (versions, logs, environment). If unknown, use 'TBD' or 'Unknown'.\n"
            "- Select exactly one template_key from the provided templates.\n"
            "- Provide values for template fields using plain text only. No backticks, no code blocks.\n"
            "- Keep title concise, no emojis.\n"
            "- Types: bug = malfunction/crash/wrong behavior; enhancement = improve existing feature; feature = new capability; question = general inquiry/clarification.\n"
            "- Priority guidance: p0=crash/data-loss/security; p1=major impact; p2=normal; p3=low; p4=cosmetic.\n"
            "- Use English for all content.\n"
        )

    def _build_user_prompt() -> str:
        return (
            "Task: Classify and return a structured JSON for the issue, including a selected template and field values.\n\n"
            f"Input Title: {(original_title or '').strip()}\n"
            f"Input Labels: {', '.join(labels)}\n\n"
            f"Input Description:\n{(description or '').strip()}\n\n"
            "Available templates (minimized schema):\n"
            + json.dumps(schema, ensure_ascii=False)
            + "\n\nOutput JSON schema (no extra keys):\n"
            "{\n"
            "  \"type\": \"bug|enhancement|feature|question\",\n"
            "  \"priority\": \"p0|p1|p2|p3|p4\",\n"
            "  \"confidence\": number,\n"
            "  \"title\": \"concise title\",\n"
            "  \"template_key\": \"one of the provided template keys\",\n"
            "  \"fields\": { \"<field.key>\": \"plain text value (no backticks)\" }\n"
            "}"
        )

    # Enforce provider presence/imports
    providers = _require_providers()

    try:
        provider = _select_single_provider()

        def call_model(messages: List[Dict[str, str]]) -> str:
            # Call exactly the selected provider. No fallbacks.
            if provider == 'deepseek':
                import openai
                client = openai.OpenAI(api_key=os.environ['DEEPSEEK_API_KEY'], base_url='https://api.deepseek.com')
                resp = client.chat.completions.create(model='deepseek-chat', messages=messages)
                return resp.choices[0].message.content
            if provider == 'openai':
                import openai
                client = openai.OpenAI(api_key=os.environ['OPENAI_API_KEY'])
                resp = client.chat.completions.create(model='gpt-4o', messages=messages)
                return resp.choices[0].message.content
            if provider == 'anthropic':
                import anthropic
                client = anthropic.Anthropic(api_key=os.environ['ANTHROPIC_API_KEY'])
                anthropic_msgs = [m for m in messages if m.get('role') != 'system']
                resp = client.messages.create(model='claude-3-haiku-20240307', max_tokens=8192, system=system_prompt, messages=anthropic_msgs)
                return resp.content[0].text
            raise RuntimeError(f"Unsupported provider: {provider}")

        # Conversation with retries for structured output
        system_msg = {"role": "system", "content": _build_system_prompt()}
        user_msg = {"role": "user", "content": _build_user_prompt()}
        messages = [system_msg, user_msg]
        result = None
        last_error = None
        for attempt in range(1, 4):
            try:
                result = call_model(messages)
            except Exception as e:
                last_error = e
                # Retry without altering messages (no provider fallback)
                continue
            if not result:
                last_error = RuntimeError('Empty model response')
            else:
                try:
                    m = re.search(r'\{[\s\S]*\}', result)
                    if not m:
                        raise ValueError('No JSON object found in AI response')
                    data = json.loads(m.group())
                    # basic validation presence and types
                    if not isinstance(data.get('fields'), dict):
                        raise ValueError('Missing or invalid fields')
                    if 'type' not in data or 'priority' not in data:
                        raise ValueError('Missing type or priority')
                    if 'template_key' not in data or not data.get('template_key'):
                        raise ValueError(f"Missing template_key. Choose one of: {', '.join(templates.keys())}")
                    tkey = str(data['template_key'])
                    if tkey not in templates:
                        raise ValueError(f"Invalid template_key '{tkey}'. Choose one of: {', '.join(templates.keys())}")
                    # required fields validation
                    required = [f['key'] for f in schema[tkey]['fields'] if f.get('required') and f.get('type') != 'markdown']
                    missing = [k for k in required if not str(data['fields'].get(k, '')).strip()]
                    # options validation for dropdown/checkboxes
                    bad_options: List[str] = []
                    by_key = {f['key']: f for f in schema[tkey]['fields']}
                    for k, v in list(data['fields'].items()):
                        meta = by_key.get(k)
                        if not meta:
                            continue
                        if meta.get('type') == 'dropdown':
                            if meta.get('options') and str(v).strip() not in meta['options']:
                                bad_options.append(f"{k} -> '{v}' (choose one of {meta['options']})")
                        if meta.get('type') == 'checkboxes':
                            vals = v if isinstance(v, list) else [s.strip() for s in str(v).split(',') if s.strip()]
                            invalid = [x for x in vals if meta.get('options') and x not in meta['options']]
                            if invalid:
                                bad_options.append(f"{k} -> {invalid} (choose from {meta['options']})")

                    if missing or bad_options:
                        reasons = []
                        if missing:
                            reasons.append('missing required fields: ' + ', '.join(missing))
                        if bad_options:
                            reasons.append('invalid option values: ' + '; '.join(bad_options))
                        raise ValueError('; '.join(reasons))

                    parsed = data
                    last_error = None
                    break
                except Exception as e:
                    last_error = e
            # add corrective message and retry
            messages.append({"role": "assistant", "content": (result or "")})
            messages.append({
                "role": "user",
                "content": (
                    "The previous output was invalid (" + str(last_error) + ").\n"
                    "Respond again with STRICT JSON only, matching the schema and template.\n"
                    "- Ensure 'template_key' is one of: " + ', '.join(templates.keys()) + "\n"
                    "- Provide all required fields with non-empty values.\n"
                    "- For dropdown/checkboxes, choose from the provided options.\n"
                )
            })

        if last_error:
            raise RuntimeError(f"Failed to obtain structured response after retries: {last_error}")

        # Try OpenAI first
        if providers['openai']:
            try:
                import openai
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
                logger.warning(f"OpenAI provider failed: {e}")

        # Try DeepSeek (OpenAI-compatible) next
        if not result and providers['deepseek']:
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
                response.raise_for_status()
                data = response.json()
                result = data.get("choices", [{}])[0].get("message", {}).get("content")
                if result:
                    logger.info("Used DeepSeek for classification")
            except Exception as e:
                logger.warning(f"DeepSeek provider failed: {e}")

        # Try Anthropic Claude
        if not result and providers['anthropic']:
            try:
                import anthropic
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
                logger.warning(f"Anthropic provider failed: {e}")

        # Parse AI response
        # Parse validated data
        data = parsed  # type: ignore[name-defined]
        itype = str(data.get('type', 'feature')).lower()
        prio = str(data.get('priority', 'p2')).lower()
        conf = float(data.get('confidence', 0.0))
        title = str(data.get('title') or original_title or '').strip()
        template_key = str(data.get('template_key') or '').strip()
        fields = data.get('fields') or {}

        if itype not in {'bug','enhancement','feature','question'}:
            raise ValueError(f'Invalid type: {itype}')
        if prio not in {'p0','p1','p2','p3','p4'}:
            prio = 'p2'
        if not title:
            title = original_title or 'Issue'
        # Strict: do not fallback to any template if invalid key slipped through
        if not template_key or template_key not in templates:
            raise ValueError(f"Invalid template_key: '{template_key}'.")

        logger.info(f"AI Classification: {itype} ({prio}) conf={conf:.2f} template={template_key}")

        label_map = {
            'bug': 'bug,needs-triage',
            'enhancement': 'enhancement,needs-triage',
            'feature': 'feature-request,needs-triage',
            'question': 'question,needs-triage',
        }
        structured = {
            'type': itype,
            'priority': prio,
            'title': title,
            'template_key': template_key,
            'fields': fields,
        }
        return itype, prio, label_map.get(itype, 'needs-triage'), title, structured

    except Exception as e:
        logger.error(f"AI classification failed: {e}")
        # Fail fast: do not fall back to keyword heuristics
        raise

    # No keyword fallback allowed by project policy
    # This function was intentionally removed to prevent heuristic misclassification.

def generate_formatted_content(template: Dict[str, Any], fields: Dict[str, Any], images: List[str]) -> str:
    """Render Markdown body using the repository template and AI-provided field values."""
    out: List[str] = []
    for item in template.get('body') or []:
        t = item.get('type')
        attrs = item.get('attributes', {})
        if t == 'markdown':
            val = attrs.get('value')
            if val:
                out.append(str(val))
            continue
        label = attrs.get('label') or t or ''
        key = item.get('id') or re.sub(r'[^a-z0-9]+', '_', str(label).lower()).strip('_')
        value_raw = fields.get(key, '')
        # Render based on field type
        if item.get('type') == 'checkboxes':
            selected = value_raw if isinstance(value_raw, list) else [s.strip() for s in str(value_raw).split(',') if s.strip()]
            lines = []
            for opt in (attrs.get('options') or []):
                lbl = opt.get('label') if isinstance(opt, dict) else str(opt)
                if not lbl:
                    continue
                mark = 'x' if lbl in selected else ' '
                lines.append(f"- [{mark}] {lbl}")
            section = f"## {label}\n" + ("\n".join(lines) if lines else '')
            out.append(section)
        else:
            value = value_raw if isinstance(value_raw, str) else json.dumps(value_raw, ensure_ascii=False)
            value = (value or '').strip()
            out.append(f"## {label}\n{value}")
    if images:
        out.append("## Screenshots\n" + "\n".join(f"![image]({u})" for u in images))
    return "\n\n".join([s for s in out if s])

def main():
    """Main processing function"""
    
    # Get issue content from environment
    issue_body = os.environ.get('ISSUE_BODY', '')
    issue_title = os.environ.get('ISSUE_TITLE', '')
    issue_number = os.environ.get('ISSUE_NUMBER', '')
    
    if not issue_body:
        logger.error("No issue body provided")
        sys.exit(1)
        
    logger.info(f"Processing issue #{issue_number}")
    
    # Load issue templates
    templates = load_issue_templates()
    
    # Extract content
    content = extract_issue_content(issue_body)
    logger.debug(f"Extracted description: {content['description'][:120]}...")
    
    # Classify issue type using AI
    # Parse labels from optional env JSON
    labels_json = os.environ.get('ISSUE_LABELS_JSON') or '[]'
    try:
        label_names = [str(x.get('name')) for x in json.loads(labels_json) if isinstance(x, dict) and x.get('name')]
    except Exception:
        label_names = []
    issue_type, priority, labels, suggested_title, structured = classify_issue_with_ai(
        content['description'], issue_title, label_names, templates
    )
    logger.info(f"Classified as: {issue_type} (priority: {priority}) title='{suggested_title}'")
    
    # Generate formatted content based on templates
    # Select the template and render the final body from AI field values
    template_key = structured.get('template_key')
    template = templates.get(template_key) or _select_template(templates, issue_type)[1]
    formatted_content = generate_formatted_content(template, structured.get('fields', {}), content.get('images', []))
    
    # Output results for GitHub Actions (current step's outputs)
    output_file = os.environ.get('GITHUB_OUTPUT')
    if not output_file:
        logger.error("GITHUB_OUTPUT not provided by the environment")
        sys.exit(3)
    with open(output_file, 'a', encoding='utf-8') as f:
        f.write(f"issue_type={issue_type}\n")
        f.write(f"priority={priority}\n")
        f.write(f"labels={labels}\n")
        f.write(f"suggested_title={suggested_title}\n")
        f.write("formatted_content<<EOF\n")
        f.write(formatted_content)
        f.write("\nEOF\n")
    
    logger.info("Processing completed successfully")

if __name__ == "__main__":
    main()
