# Contributing

Thank you for considering a contribution to the TIM Validator, an ITS Joint
Program Office (ITS JPO), U.S. Department of Transportation project. Noblis
provides systems engineering for the project. If you are unsure about anything,
open an issue or pull request and the maintainers will help.

## Licensing status

As noted in [LICENSE.md](LICENSE.md), this project is licensed under the Apache
License, Version 2.0.

All contributions to this project will be released under the same Apache 2.0
license. By submitting a pull request, you agree that your contribution may be
licensed under those terms, without additional conditions, unless you
conspicuously mark the submission as "Not a Contribution."

## Code of conduct

Participants are expected to:

- Be respectful and professional in issues, pull requests, and reviews.
- Focus discussion on the technical merits of the change.
- Avoid harassment, personal attacks, and off-topic discussion.

Unacceptable behavior may be reported to the project contact listed in the
[README.md](README.md) Contact Information section.

## How to contribute

- Report bugs and request features through the project's issue tracker.
- Open a pull request with a focused bug fix or new functionality.
- Improve documentation, tests, or examples.
- Review and comment on issues or pull requests opened by other users.

## Contributing process

Most pull requests should target the default development branch. Merged changes
are included in the next snapshot or release. If a bug fix needs to land on a
release branch, merge it to the development branch first, then open a
follow-up pull request that cherry-picks the commits onto the release branch.

A maintainer will be assigned to review each pull request. Small cleanups may
be merged after an initial review. Larger changes may require discussion or
revisions. Maintainers aim to respond within 7 days; if you have not heard back
after a few days, comment on the thread. Contributors are likewise expected to
respond to review comments in a reasonable time. Pull requests with no response
for two weeks or longer may be closed and can be reopened when work resumes.

Once a pull request is merged, maintainers will include it in the next snapshot
or release as appropriate.

## Issue guidelines

Issues should be:

- **Specific.** Request a concrete change (for example, `Reject empty ITWG content arrays`) rather than a vague goal (`Improve validation`).
- **Measurable.** Include enough acceptance criteria that the issue can be closed when those criteria are met.
- **Actionable.** Ask for something a maintainer or community member can do in this repository.
- **Realistic.** Stay within the resources of the project, including community contributions.
- **Time-aware.** Prefer work that can be completed within about six months. Break larger efforts into smaller issues.

Questions about using the software are welcome in issues when they include the
input payload, expected result, actual result, and environment (Java version,
operating system, and whether you used the library, API, or Docker image).

## Pull request guidelines

- Keep pull requests small and focused on a single concern.
- For speculative or large changes, open an issue and discuss the approach first.
- Describe what changed and why. Link the related issue when one exists.
- Include or update tests for behavior changes. Library tests must not call live Overpass or other network services.
- Update documentation when user-facing behavior, configuration, or schemas change.
- Keep a clean commit history and use meaningful commit messages.
- Keep the branch up to date with the target branch so it can be merged.

Pull requests that mix unrelated refactors, formatting-only churn, or generated
files without explanation may be asked to split or revise before review.

## Reviewer guidelines

Reviewers should note whether a change belongs in the next [CHANGELOG.md](CHANGELOG.md)
entry (new features and user-visible bug fixes) or is internal only (refactors,
test-only updates, and documentation typo fixes).

Contributions are released in accordance with the repository license. See
[GitHub's terms covering contributions under a repository license](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service#6-contributions-under-repository-license)
when contributing through GitHub.
