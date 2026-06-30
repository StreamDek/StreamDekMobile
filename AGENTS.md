# AGENTS.md

## File Editing

Always edit files using the native apply_patch tool.

Do NOT:
- use PowerShell to rewrite files
- use Set-Content
- use Out-File
- use Add-Content
- generate temporary Python scripts to edit files
- use Perl or other scripting languages for file modifications

Only use shell commands for:
- builds
- tests
- git
- searching
- diagnostics

For every code change, use apply_patch.