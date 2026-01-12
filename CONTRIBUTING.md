# Contributing

This plugin is designed to make it easier for multiple developers to implement their own features.

## How to contribute

| Contribution      | Details                                                                                                                                                       |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Report a bug      | You can [file an issue](https://github.com/Darkbot-Plugins/SharedPlugin/issues/new/choose) To report a bug.                                                   |
| Contribute code   | You can contribute your code by fixing a bug or implementing a feature.                                                                                       |
| Help to translate | You can help translate the plugin from .                                                                                                                      |
| Help in discord   | There are many users asking for help on discord. [Coding Channel in the official Discord](https://discord.com/channels/523159099870019584/700042416576659456) |

## Contribute Code

To contribute code, please use the fork-and-pull workflow. This makes it easy for anyone to propose changes without needing write access to the main repository. All contributions will be reviewed and must follow DarkBot's rules.

Step-by-step guide

1) Create a fork

- On GitHub, click the "Fork" button in the top-right corner of the repository page to create a copy under your account.

Note: You only need to create a fork the first time you contribute. If you already have a fork, skip this step and clone or update your existing fork.

2) Clone your fork

- If you already have a fork and cloned it previously, first sync your fork on GitHub with the upstream repository (recommended), then update your local clone. 

On GitHub: open your fork page → click "Sync fork" → "Update branch".

```bash
# after syncing your fork on GitHub (Sync fork → Update branch):
git checkout main
git fetch origin
git pull --rebase origin main
```

- else clone your fork with:

```bash
git clone git@github.com:<your-username>/SharedPlugin.git
cd SharedPlugin
```

3) In the fork create a branch

```bash
git checkout -b my-feature-branch
```

4) Add your code and test it

- Implement your changes and run any local tests or manual checks.
- This plugin depends on [DarkBotAPI](https://github.com/darkbot-reloaded/DarkBotAPI) and [DarkBot](https://github.com/darkbot-reloaded/DarkBot). Prefer using `DarkBotAPI` instead of referencing `DarkBot` directly.

5) Push your branch to your fork

```bash
git add .
git commit -m "Short, descriptive message"
git push -u origin my-feature-branch
```

6) Make a Pull Request to the main repository

- On GitHub, open a Pull Request from your fork/branch to the main repository's `main` branch (or the branch indicated in the repo). Describe what your change does and link any related issues.

Notes
- Follow repository coding conventions and keep changes focused and small when possible.
- If your PR requires special testing steps, include them in the PR description so reviewers can reproduce.

### Build the plugin

As I use graddle you can run the task 'copyFile' and it will generate a jar called SharedPlugin.jar.

### How to test the changes

When a pull request is made, I will build a version with the changes so that they can be tested.
Each time a merge is performed, a new version will be deployed and the .jar file will be added.
