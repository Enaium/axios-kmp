# Contribution Rules

These rules apply to every contributor, including contributors using AI-assisted
development.

## 1. AI Usage and Collaborator Attribution

- AI-assisted development is allowed for code, tests, documentation, and
  project configuration.
- Every commit produced with AI assistance must add the AI as a co-author:

  ```text
  Co-authored-by: <AI name> <email>
  ```

  Example:

  ```text
  feat(client): add request timeout mapping

  Co-authored-by: Example AI <ai@example.com>
  ```

- The human submitter remains responsible for reviewing AI-generated work and
  for its correctness, security, tests, and license compliance.
- If an AI operates a repository account that pushes branches directly, add
  that account to the repository collaborators with the minimum required
  permission.
- Do not invent co-author identities. Use the actual AI name and, when
  available, its real email address.

## 2. No Merge Commits; Squash into One Commit

- Merge commits are prohibited. Do not use `git pull` in a way that creates a
  merge commit; sync with the target branch using `git rebase`.
- A pull request must ultimately contain **exactly one commit**.
- Squash every commit belonging to the same change into that single commit,
  including:
  - repeated edits of the same type,
  - `fixup!` and `squash!` commits,
  - formatting-only commits,
  - documentation follow-ups.
- Use GitHub's **Squash and merge** option, or squash locally with
  `git rebase -i` and integrate the result with a fast-forward.
- The target branch history must stay linear and must not contain merge
  commits.

## 3. One Feature per Pull Request

- Each pull request must address exactly one feature, one bug fix, or one
  independent refactor.
- Multiple unrelated features must be submitted as multiple pull requests.
- Do not mix unrelated changes in the same pull request. Split them so each
  change can be reviewed and squashed independently.
- Keep the PR title, commit message, and scope specific to that single change,
  for example:

  ```text
  feat(adapter): support per-request Ktor engines
  fix(headers): preserve repeated Set-Cookie values
  docs(readme): document local Maven publication
  ```

## Pre-submit Checklist

- [ ] The pull request contains exactly one feature, fix, or refactor.
- [ ] Multiple independent features are split into separate pull requests.
- [ ] The branch is rebased onto the target branch and contains no merge commit.
- [ ] The pull request contains exactly one commit.
- [ ] AI-assisted commits add the AI as a co-author.
- [ ] A directly pushing AI account is registered as a repository collaborator.
