# Generate PRP

## Feature file: $ARGUMENTS

Generate a complete PRP for implementing a feature in Mars Enterprise Kit Lite. Read the feature file first to understand what needs to be created, how the examples provided help, and any other considerations.

The executor only gets the context you append to the PRP plus access to the codebase and web search. It does NOT have memory of this generation phase. Your research findings must be included or referenced in the PRP — pass URLs to documentation and examples.

## Research Process

1. **Codebase Analysis**
   - Read `CLAUDE.md` for project rules and conventions
   - Read `.mars/docs/mars-enterprise-kit-context-lite.md` for project context
   - Search for similar features/patterns in existing code
   - Identify files to reference in the PRP
   - Note existing conventions to follow
   - Check test patterns for the validation approach

2. **External Research** (if needed)
   - Library documentation (include specific URLs with relevant sections)
   - Implementation examples (GitHub/StackOverflow/blogs)
   - Best practices and common pitfalls

3. **User Clarification** (if needed)
   - Specific business rules or validation requirements
   - Integration requirements and where to find them
   - Specific patterns to mirror and where to find them

## PRP Generation

Use `.mars/PRPs/templates/prp_base.md` as the template.

### Critical Context to Include and Pass as Part of the PRP
- **Documentation**: URLs with specific sections
- **Code Examples**: Real snippets from existing codebase
- **Gotchas**: Library quirks, version issues, project-specific constraints
- **Patterns**: Existing approaches to follow

### Implementation Blueprint
- Start with pseudocode showing the approach
- Reference real files for patterns
- Include error handling strategy
- List tasks to be completed to fulfill the PRP in the order they should be completed

### Validation Gates (Must be Executable)
```bash
# 1. Compile all modules
mvn clean compile

# 2. Run unit tests (fast, no Spring context)
mvn test -pl business

# 3. Run data-provider integration tests
mvn test -pl data-provider

# 4. Run app tests (service integration + E2E)
mvn test -pl app

# 5. Full build verification
mvn clean verify
```

---

*** CRITICAL: AFTER RESEARCHING AND EXPLORING THE CODEBASE, BEFORE WRITING THE PRP ***

*** ULTRATHINK ABOUT THE PRP AND PLAN YOUR APPROACH, THEN START WRITING ***

---

## Output

Save as: `.mars/PRPs/{feature-name}.md`

## Quality Checklist
- [ ] All necessary context included
- [ ] Validation gates are executable
- [ ] References existing patterns from codebase
- [ ] Clear implementation path
- [ ] Error handling documented

Score the PRP on a scale of 1-10 (confidence level for one-pass implementation success).

**Remember**: The goal is one-pass implementation success through comprehensive context.
