# Contributing · 贡献指南

Issues and pull requests in Chinese or English are welcome. 欢迎用中文或英文反馈问题和提交修改。

## Report a problem or propose a change

- Include the app version, Android version, selected sources, and the smallest steps to reproduce an issue. [Open an issue](https://github.com/cdja390/NClientV3-Modern-src/issues/new/choose)
- For a feature request, describe the workflow you want to improve and what you expect to happen.
- Remove cookies, tokens, account details, and personal paths from logs and screenshots before sharing them. Report vulnerabilities privately. [Security policy](SECURITY.md)

## Make a change

1. Base your branch on `main` and keep one pull request focused on one change. Explain what a user or maintainer will observe after the change.
2. For UI text, update the default English, Simplified Chinese (`zh-rCN`), and Traditional Chinese (`zh-rTW`) resources together. Keep shared placeholders consistent. Update both READMEs when their instructions change.
3. For changes to browsing, history, favorites, or downloads, preserve source identity. EH/EX galleries may share numeric IDs with NH and need their gallery tokens. Keep the NH DB v16 schema and the additive backup format compatible. [Backup format](docs/BACKUP_FORMAT.md)
4. Run the checks that cover your change. For Android code, build Debug and unsigned Release and run the Debug unit tests. Use the project's Gradle Wrapper and JDK 21. [Commands and report locations](docs/BUILD.md)
5. In your pull request, record the checks you ran and their results. For device checks, include the Android version and build variant. Include screenshots when they help explain a UI change.

## Project references

- Release history and user-visible changes. [Changelog](CHANGELOG.md)
- Source merging, reader, upgrade, and backup scenarios. [4.7.2 acceptance matrix](docs/MERGE_ACCEPTANCE_472.md)
- Upstream attribution and adapted files. [Patch map](docs/PATCHED_FILES.md)
