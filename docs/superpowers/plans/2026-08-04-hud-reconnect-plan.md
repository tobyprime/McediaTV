# HUD Reconnect Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix persistent HUD playback after reconnect for the 1.21.11 client and add enough lifecycle logging to diagnose any remaining failure.

**Architecture:** The existing server binding protocol remains authoritative. The client no longer destroys channel sessions during `JOIN`; it destroys them only during `DISCONNECT`, with HUD peripherals detached before host sessions are cleared. Logging is added at each client connection, binding, session, payload routing, and packet-send boundary.

**Tech Stack:** Java 21, Fabric API 0.140.2+1.21.11, JUnit 5, Gradle/Fabric Loom.

## Global Constraints

- Modify only the shared 1.21.11 client sources and tests in this pass.
- Do not add network packets or modify the Paper plugin.
- Preserve unrelated user changes in the worktree.
- Production code must have a failing regression test before implementation.

---

### Task 1: Add the lifecycle regression test

**Files:**
- Create: `mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/channel/MtvClientConnectionLifecycleTest.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/MtvClientConnectionLifecycle.java`

**Interfaces:**
- Produces `MtvClientConnectionLifecycle.onJoin()` and `MtvClientConnectionLifecycle.onDisconnect(Runnable hudCleanup, Runnable sessionClear)` for the connection callbacks.

- [ ] **Step 1: Write the failing test**

Test that `onJoin()` does not invoke cleanup, and that `onDisconnect()` invokes HUD cleanup before session clearing.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew.bat --no-daemon :mcedia-mtv-1.21.11:test --tests top.tobyprime.mcedia_mtv.client.channel.MtvClientConnectionLifecycleTest`

Expected: compilation/test failure because `MtvClientConnectionLifecycle` does not yet exist.

- [ ] **Step 3: Add the minimal lifecycle policy implementation**

Implement `onJoin()` as a no-op policy method and `onDisconnect(...)` as the ordered two-callback operation.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same Gradle test command and expect one passing test class.

### Task 2: Wire 1.21.11 connection handling

**Files:**
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/MtvClientChannelPayloads.java`
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/EntityPlayerClientEntryPoint.java`

**Interfaces:**
- Consumes `MtvClientConnectionLifecycle` from Task 1.
- `JOIN` logs the transition without clearing sessions.
- `DISCONNECT` invokes HUD cleanup before `ClientChannelPlaybackManager.clear()`.

- [ ] **Step 1: Add the failing integration-facing assertion**

Use the lifecycle test from Task 1 as the behavioral contract; no game-client mock is introduced.

- [ ] **Step 2: Wire the minimal callbacks**

Remove the `JOIN` clear call, call the lifecycle policy from `onJoin`/`onDisconnect`, and remove the duplicate `DISCONNECT` registration from `EntityPlayerClientEntryPoint`.

- [ ] **Step 3: Add connection logs**

Log server identity and explicit begin/end messages for join and disconnect, including that join retains sessions and disconnect clears them.

- [ ] **Step 4: Run focused test and compile**

Run: `./gradlew.bat --no-daemon :mcedia-mtv-1.21.11:test --tests top.tobyprime.mcedia_mtv.client.channel.MtvClientConnectionLifecycleTest`

### Task 3: Add diagnostics at HUD and channel boundaries

**Files:**
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/HudChannelPlayer.java`
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/ClientChannelPlaybackManager.java`
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/ClientChannelSession.java`
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/MtvClientChannelPayloads.java`
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/MtvChannelClientPacketSender.java`

**Interfaces:**
- Preserve all existing public methods and packet formats.
- Log channel ID, revision, session map state, host ID where available, attach counts, and rejection reasons.

- [ ] **Step 1: Add binding and routing logs**

Log every binding payload, duplicate-binding decision, subscribe success/failure, unsubscribe, snapshot ignored/applied, and remove packet.

- [ ] **Step 2: Add session and send-boundary logs**

Log session creation/destruction, attach/detach counts, clear count, packet-send rejection reason, and successful packet type sends.

- [ ] **Step 3: Run tests**

Run: `./gradlew.bat --no-daemon :mcedia-mtv-1.21.11:test`

### Task 4: Build and scope verification

**Files:**
- No additional source files.

- [ ] **Step 1: Build 1.21.11**

Run: `./gradlew.bat --no-daemon :mcedia-mtv-1.21.11:build`

- [ ] **Step 2: Verify scope**

Run: `git diff --name-only` and confirm only the 1.21.11 shared client sources, tests, and the two plan/design documents changed; confirm no `versions/26.1` or `versions/26.2` Java files changed.
