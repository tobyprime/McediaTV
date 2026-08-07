# MTV World Player UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an MPV-style in-world MTV screen controller with authoritative server controls, paged shared playlists, and locally resolved media previews without changing McediaCore.

**Architecture:** Keep channel state and all mutations in `mcedia-mtv-plugin`; extend the existing custom-payload transport with bounded codecs and capability negotiation. The client keeps UI, hit testing, metadata and page caches local, while each supported Minecraft-version source set supplies renderer and input hooks that translate the same `worldui` model to its Minecraft APIs.

**Tech Stack:** Java 21/25, Fabric networking/rendering, Paper/Folia plugin messaging, JUnit 5, existing Mcedia media resolvers and player peripheral APIs.

## Global Constraints

- Do not modify `G:\workspaces\minecraft\McediaCore`.
- Keep the Bukkit container GUI responsible for external peripheral configuration and per-speaker settings.
- Server owns channel revision, playlist, playback state and MTV master volume; client-provided world coordinates are never trusted.
- Playlist pages contain at most 32 entries and at most 24 KiB encoded payload; every URL is at most 2048 characters.
- No packet may be emitted for mouse movement, hover, drag preview, render frame, metadata resolution or cover downloading.
- A control request must include target MTV UUID, screen ID, channel ID, request ID, expected revision and normalized screen UV.
- A client must not show or send world-control packets until it has received `world_ui_capabilities`.
- Build and retain compatibility for 1.21.11, 26.1 and 26.2.

---

## File Structure

- `mcedia-mtv/src/client/java/.../channel/MtvChannelProtocol.java`: shared bounded binary codecs and protocol constants.
- `mcedia-mtv/src/client/java/.../channel/worldui/*`: immutable payload records, capability state, page cache and request sender.
- `mcedia-mtv/src/client/java/.../metadata/*`: client-only resolver and bounded media/cover cache.
- `mcedia-mtv/src/client/java/.../worldui/*`: version-independent interaction state, normalized geometry, layout and controls.
- `mcedia-mtv/versions/<version>/src/client/java/.../worldui/*`: Fabric renderer, ray-input hook and full-screen add-media screen for each Minecraft mapping.
- `mcedia-mtv-plugin/src/main/java/.../channel/worldui/*`: message decoding, rate limit, manifest/page publisher, watch registry and control dispatcher.
- `mcedia-mtv-plugin/src/main/java/.../worldui/*`: server screen-plane and occlusion validator.
- Existing `MtvChannelService`, `MtvChannelNetworkService`, `MtvPlaybackController`, `MtvPlayerManager` and `ManagedMtvPlayer`: expose controlled state/query operations and register the new network service.
- Unit tests in the matching `src/test/java` trees prove codec limits, cache behavior and mutation/security decisions without a running server.

### Task 1: Establish the protocol model and bounded codec tests

**Files:**
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiControlOperation.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiControlRequest.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiControlResult.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiPlaylistManifest.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiPlaylistPage.java`
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/MtvChannelProtocol.java`
- Test: `mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiProtocolTest.java`

**Interfaces:**
- Produces `WorldUiControlRequest(UUID targetMtvUuid, String screenId, String channelId, long requestId, long expectedRevision, float hitU, float hitV, WorldUiControlOperation operation, String mediaUrl, int index, long value)`.
- Produces `MtvChannelProtocol.readPlaylistPage(FriendlyByteBuf)` and `writePlaylistPage(FriendlyByteBuf, WorldUiPlaylistPage)`.

- [ ] **Step 1: Write failing codec-boundary tests**

```java
@Test
void playlistPageStopsAtBothItemAndEncodedByteLimit() {
    var urls = Collections.nCopies(33, "https://example.test/" + "x".repeat(900));
    assertThrows(IllegalArgumentException.class, () ->
            MtvChannelProtocol.encodePlaylistPage(new WorldUiPlaylistPage("c", 4L, 33, 0, "LOOP", 0, urls)));
}

@Test
void decodeRejectsTrailingBytesAndOverlongUrls() {
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    buffer.writeUtf("c");
    buffer.writeLong(1L);
    buffer.writeVarInt(0);
    buffer.writeByte(9);
    assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.readPlaylistManifest(buffer));
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiProtocolTest'`

Expected: compilation fails because the world UI protocol types do not exist.

- [ ] **Step 3: Implement the bounded records and codec helpers**

```java
private static String readBoundedUtf(FriendlyByteBuf buffer, int maxLength, String field) {
    String value = buffer.readUtf(maxLength);
    if (value.length() > maxLength) throw invalidPacket("world UI", field + " too long");
    return value;
}

private static void requirePage(List<String> urls) {
    if (urls.size() > 32) throw new IllegalArgumentException("playlist page exceeds 32 entries");
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiProtocolTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/channel
git commit -m "feat: add bounded world UI protocol model"
```

### Task 2: Add payload registration and capability-gated client transport

**Files:**
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/worldui/MtvWorldUiPayloads.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiCapabilityState.java`
- Create: payload wrappers for capabilities, manifest, page request/page, control request/result and watch/unwatch.
- Modify: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/MtvClientChannelPayloads.java`
- Test: `mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiCapabilityStateTest.java`

**Interfaces:**
- Consumes protocol records from Task 1.
- Produces `WorldUiCapabilityState.supported()`, `onCapabilities(int version, int pageSize, long features)` and `clear()`.
- Produces `MtvWorldUiPayloads.sendControl(WorldUiControlRequest)` which returns without networking when capability negotiation has not succeeded.

- [ ] **Step 1: Write capability reset and gate tests**

```java
@Test
void controlsRemainDisabledUntilMatchingCapabilitiesArrive() {
    var state = new WorldUiCapabilityState();
    assertFalse(state.supported());
    state.onCapabilities(1, 32, 0L);
    assertTrue(state.supported());
    state.clear();
    assertFalse(state.supported());
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiCapabilityStateTest'`

Expected: FAIL because `WorldUiCapabilityState` is missing.

- [ ] **Step 3: Register payloads and reset capability/page state at disconnect**

```java
ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
    WorldUiCapabilityState.getInstance().clear();
    WorldUiPlaylistCache.getInstance().clear();
});
```

- [ ] **Step 4: Run the focused test and existing lifecycle test**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiCapabilityStateTest' --tests '*MtvClientConnectionLifecycleTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/channel
git commit -m "feat: negotiate MTV world UI capabilities"
```

### Task 3: Implement sparse playlist cache and local metadata resolution

**Files:**
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiPlaylistCache.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/metadata/MtvMediaMetadata.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/metadata/MtvMediaMetadataCache.java`
- Test: `mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/channel/worldui/WorldUiPlaylistCacheTest.java`
- Test: `mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/metadata/MtvMediaMetadataCacheTest.java`

**Interfaces:**
- Consumes `WorldUiPlaylistManifest`/`Page` from Task 1 and `MediaResolvers.resolve(String)`.
- Produces `applyManifest`, `applyPage`, `missingOffsetsForVisibleRange`, and `metadata.resolveAsync(String)`.

- [ ] **Step 1: Write failing cache tests**

```java
@Test
void newRevisionDropsPagesButKeepsUrlMetadataKeys() {
    cache.applyPage(new WorldUiPlaylistPage("c", 3L, 64, 0, "LOOP", 0, List.of("a")));
    cache.applyManifest(new WorldUiPlaylistManifest("c", 4L, 64, 0, "LOOP"));
    assertTrue(cache.pageAt("c", 0).isEmpty());
}
```

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiPlaylistCacheTest' --tests '*MtvMediaMetadataCacheTest'`

Expected: FAIL because caches are absent.

- [ ] **Step 3: Implement request de-duplication, LRU metadata cache and asynchronous resolver mapping**

```java
public CompletableFuture<MtvMediaMetadata> resolveAsync(String url) {
    String key = normalizeUrl(url);
    return entries.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> resolve(key), executor));
}
```

Read description only from a documented `MediaInfo.extraMetadata()` key; map absent values to `""`, never submit metadata to the plugin, and cap cached entries and in-flight tasks.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiPlaylistCacheTest' --tests '*MtvMediaMetadataCacheTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/{channel,metadata} mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client
git commit -m "feat: cache paged MTV playlist metadata locally"
```

### Task 4: Publish manifests and bounded pages from the plugin

**Files:**
- Create: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/PlaylistPageEncoder.java`
- Create: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/WorldUiPlaylistPublisher.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/MtvChannelNetworkService.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/MtvChannelProtocol.java`
- Test: `mcedia-mtv-plugin/src/test/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/PlaylistPageEncoderTest.java`

**Interfaces:**
- Consumes `ChannelRuntimeState.getPlaylist()`, revision, cursor and `ChannelPlayOrderMode`.
- Produces `encodePage(ChannelRuntimeState state, int offset): WorldUiPlaylistPage` and `publishManifest(String channelId)`.

- [ ] **Step 1: Write page boundary tests**

```java
@Test
void encoderNeverProducesMoreThan32EntriesOr24KiB() {
    var page = encoder.encodePage(stateWithUrls(80, 1000), 0);
    assertTrue(page.urls().size() <= 32);
    assertTrue(MtvChannelProtocol.encodePlaylistPage(page).length <= 24 * 1024);
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :mcedia-mtv-plugin:test --tests '*PlaylistPageEncoderTest'`

Expected: FAIL because no page encoder exists.

- [ ] **Step 3: Encode pages and broadcast only manifests after playlist mutations**

```java
public void onChannelChanged(String channelId) {
    publishSnapshot(channelId);
    worldUiPublisher.publishManifest(channelId);
}
```

The encoder rejects negative/misaligned offsets and skips no valid entry merely because an earlier item fits; when the first valid URL alone cannot fit, fail the request instead of creating an oversized packet.

- [ ] **Step 4: Run focused plugin tests**

Run: `./gradlew :mcedia-mtv-plugin:test --tests '*PlaylistPageEncoderTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel mcedia-mtv-plugin/src/test/java/top/tobyprime/mcedia_mtv_plugin/channel
git commit -m "feat: publish MTV playlist manifests and pages"
```

### Task 5: Add authoritative control dispatch and rate limiting

**Files:**
- Create: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/WorldUiControlDispatcher.java`
- Create: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/WorldUiRateLimiter.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/MtvChannelService.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/controller/MtvPlaybackController.java`
- Test: `mcedia-mtv-plugin/src/test/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/WorldUiControlDispatcherTest.java`

**Interfaces:**
- Consumes Task 1 request and `MtvChannelService` playback/playlist methods.
- Produces `dispatch(Player, WorldUiControlRequest): WorldUiControlResult` and stable error codes from the design.

- [ ] **Step 1: Write revision, operation and request-budget tests**

```java
@Test
void stalePlaylistMutationIsRejectedWithoutChangingState() {
    var result = dispatcher.dispatch(player, appendRequest(knownRevision - 1));
    assertEquals(WorldUiControlError.STALE_REVISION, result.error());
    assertEquals(beforeUrls, state.getPlaylist());
}

@Test
void tenthControlIsAcceptedAndEleventhIsRateLimited() { /* inject clock, dispatch 11 requests */ }
```

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `./gradlew :mcedia-mtv-plugin:test --tests '*WorldUiControlDispatcherTest'`

Expected: FAIL because dispatcher types do not exist.

- [ ] **Step 3: Implement explicit validation and operation mapping**

```java
if (request.expectedRevision() != state.getRevision() && request.operation().changesChannelRevision()) {
    return rejected(request.requestId(), STALE_REVISION, state.getRevision());
}
return switch (request.operation()) {
    case TOGGLE_PAUSE -> mutate(channelService::togglePause);
    case SET_SPEED -> mutate(() -> channelService.updateSpeed(channelId, checkedSpeed(request.value())));
    case APPEND -> mutate(() -> channelService.appendPlaylistItem(channelId, checkedUrl(request.mediaUrl())));
    // Map every remaining enum member explicitly; default is impossible.
};
```

Implement `SET_MASTER_VOLUME` against the MTV entity without changing channel revision. Require a nonblank normalized URL, valid index and domain bounds before calling any mutation.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew :mcedia-mtv-plugin:test --tests '*WorldUiControlDispatcherTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/{channel,controller} mcedia-mtv-plugin/src/test/java/top/tobyprime/mcedia_mtv_plugin/channel
git commit -m "feat: validate MTV world UI control requests"
```

### Task 6: Validate server-side target, screen geometry and block occlusion

**Files:**
- Create: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/worldui/WorldUiScreenHitValidator.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/model/ManagedMtvPlayer.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/model/ScreenPeripheralConfigModel.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/WorldUiControlDispatcher.java`
- Test: `mcedia-mtv-plugin/src/test/java/top/tobyprime/mcedia_mtv_plugin/worldui/WorldUiScreenHitValidatorTest.java`

**Interfaces:**
- Produces `validate(Player player, ManagedMtvPlayer target, String screenId, float u, float v): ValidationResult`.
- `ValidationResult` distinguishes `SCREEN_NOT_FOUND`, `WORLD_MISMATCH`, `INVALID_ARGUMENT` and `OCCLUDED`.

- [ ] **Step 1: Write pure geometry tests using a screen transform fixture**

```java
@Test
void outOfRangeUvAndSolidBlockBeforeHitAreRejected() {
    assertEquals(INVALID_ARGUMENT, validator.validate(player, target, "main", -0.01F, 0.5F).error());
    world.placeSolidBlockBetween(player.getEyeLocation(), expectedHit);
    assertEquals(OCCLUDED, validator.validate(player, target, "main", 0.5F, 0.5F).error());
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :mcedia-mtv-plugin:test --tests '*WorldUiScreenHitValidatorTest'`

Expected: FAIL because the validator does not exist.

- [ ] **Step 3: Rebuild the hit point only from server peripheral transform data**

```java
Location hit = screenTransform.toWorld(u, v);
RayTraceResult trace = player.getWorld().rayTraceBlocks(player.getEyeLocation(), hit.toVector().subtract(player.getEyeLocation().toVector()));
if (trace != null && trace.getHitPosition().distanceSquared(hit.toVector()) + EPSILON < eye.distanceSquared(hit)) return occluded();
```

Verify target UUID lookup, target screen membership, current channel binding and same-world condition before permission checks. Do not add an interaction-distance check.

- [ ] **Step 4: Run focused test**

Run: `./gradlew :mcedia-mtv-plugin:test --tests '*WorldUiScreenHitValidatorTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/{worldui,channel,model} mcedia-mtv-plugin/src/test/java/top/tobyprime/mcedia_mtv_plugin/worldui
git commit -m "feat: verify world UI screen visibility server-side"
```

### Task 7: Register plugin messages, capability negotiation and state watches

**Files:**
- Create: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/WorldUiWatchRegistry.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/channel/MtvChannelNetworkService.java`
- Modify: `mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/McediaMtvPlugin.java`
- Test: `mcedia-mtv-plugin/src/test/java/top/tobyprime/mcedia_mtv_plugin/channel/worldui/WorldUiWatchRegistryTest.java`

**Interfaces:**
- Produces `watch(UUID playerId, UUID mtvId)`, `unwatch(UUID playerId)`, `watchers(UUID mtvId)` and bounded `WorldUiControlState` sending.

- [ ] **Step 1: Write registry tests**

```java
@Test
void eachPlayerWatchesOnlyOneMtvAndChangingTargetRemovesOldWatch() {
    registry.watch(player, first);
    registry.watch(player, second);
    assertTrue(registry.watchers(first).isEmpty());
    assertEquals(Set.of(player), registry.watchers(second));
}
```

- [ ] **Step 2: Run focused test and verify it fails**

Run: `./gradlew :mcedia-mtv-plugin:test --tests '*WorldUiWatchRegistryTest'`

Expected: FAIL because the registry is absent.

- [ ] **Step 3: Register channels and send only event-driven state**

```java
if (messageChannel.equals(MtvChannelProtocol.WORLD_UI_WATCH)) {
    watchRegistry.watch(player.getUniqueId(), request.targetMtvUuid());
    sendControlState(player, request.targetMtvUuid());
}
```

Send capabilities after the existing subscription path succeeds; remove watches on quit, entity removal and plugin shutdown. Never schedule a UI-state heartbeat.

- [ ] **Step 4: Run focused test and existing network service tests**

Run: `./gradlew :mcedia-mtv-plugin:test --tests '*WorldUiWatchRegistryTest' --tests '*MtvChannelNetworkServiceTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin/{channel,McediaMtvPlugin.java} mcedia-mtv-plugin/src/test/java/top/tobyprime/mcedia_mtv_plugin/channel
git commit -m "feat: wire MTV world UI network watches"
```

### Task 8: Build the version-independent in-world UI interaction model

**Files:**
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/worldui/WorldUiLayout.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/worldui/WorldUiInteractionState.java`
- Create: `mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/worldui/WorldUiHit.java`
- Test: `mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/worldui/WorldUiLayoutTest.java`

**Interfaces:**
- Produces `WorldUiLayout.hit(float u, float v, boolean expanded)` and `WorldUiInteractionState.onPrimaryPress/onPrimaryRelease`.
- Consumes client control sender from Task 2 and manifest/page cache from Task 3.

- [ ] **Step 1: Write layout and drag-only-on-release tests**

```java
@Test
void bottomRightToggleIsVisibleOnlyWhilePointerIsInTriggerOrButton() {
    assertTrue(layout.hit(0.98F, 0.98F, false).isToggle());
    assertFalse(layout.hit(0.50F, 0.50F, false).isToggle());
}

@Test
void seekSendsOnceOnRelease() { /* press progress, drag twice, release; assert one request */ }
```

- [ ] **Step 2: Run focused test and verify it fails**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiLayoutTest'`

Expected: FAIL because the world UI model is absent.

- [ ] **Step 3: Implement normalized MPV-style control hit regions**

```java
if (activeDrag == Drag.SEEK && primaryReleased) {
    sender.sendSeek(target, previewProgress);
    activeDrag = Drag.NONE;
}
```

Keep hover, expanded/collapsed state, drag preview and local mute restore volume entirely in `WorldUiInteractionState`; collapse must unwatch the old target.

- [ ] **Step 4: Run focused test**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiLayoutTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/worldui mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/worldui
git commit -m "feat: model MTV world screen controls locally"
```

### Task 9: Add version-specific renderer, ray input and screen progress overlay

**Files:**
- Create for each supported version: `mcedia-mtv/versions/1.21.11/src/client/java/.../worldui/MtvWorldUiRenderer.java`, `mcedia-mtv/versions/26.1/src/client/java/.../worldui/MtvWorldUiRenderer.java`, `mcedia-mtv/versions/26.2/src/client/java/.../worldui/MtvWorldUiRenderer.java`.
- Create matching `MtvWorldUiInputHook.java` and mixin/config registrations where mapping-specific input interception is required.
- Modify version-specific client entrypoints to register rendering and tick/input hooks.
- Modify plugin peripheral creation/update path to set MTV-managed screen `progressBarVisible=false`.

**Interfaces:**
- Consumes `WorldUiInteractionState`, MTV entity/screen configuration and client channel snapshot.
- Produces a world-plane renderer that uses local screen geometry and cancels attack/use only when a UI hot region consumed the click.

- [ ] **Step 1: Add a mapping-specific smoke-test harness or render-independent transform test for each source set**

```java
@Test
void screenTransformMapsCenterAndBottomRightToExpectedWorldPlane() {
    assertEquals(new Vec2(0.5F, 0.5F), transform.fromRay(centerRay).uv());
}
```

- [ ] **Step 2: Run each focused version test and verify it fails**

Run: `./gradlew :mcedia-mtv-1.21.11:test :mcedia-mtv-26.1:test :mcedia-mtv-26.2:test --tests '*WorldUi*Test'`

Expected: FAIL until version adapters are present.

- [ ] **Step 3: Render only screen-local controls after the ray has selected the nearest front-facing MTV screen**

```java
if (interaction.isExpanded(target) || interaction.shouldShowToggle(hit)) {
    renderer.drawControls(poseStack, screenPlane, interaction.viewModel(target));
}
```

Use the normal depth pipeline so blocks occlude the overlay. For undersized/abnormal screens render only transport, progress and queue controls; do not release the cursor or alter camera control.

- [ ] **Step 4: Build all version adapters**

Run: `./gradlew :mcedia-mtv-1.21.11:compileJava :mcedia-mtv-26.1:compileJava :mcedia-mtv-26.2:compileJava`

Expected: all compile tasks succeed.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv/versions mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/worldui mcedia-mtv-plugin/src/main/java/top/tobyprime/mcedia_mtv_plugin
git commit -m "feat: render controllable MTV screen overlay"
```

### Task 10: Add full-screen local URL preview and playlist controls

**Files:**
- Create matching version-specific `MtvAddMediaScreen.java` files.
- Modify matching `MtvWorldUiRenderer.java` and `WorldUiInteractionState.java`.
- Test: `mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/worldui/WorldUiAddMediaModelTest.java`

**Interfaces:**
- Consumes `MtvMediaMetadataCache.resolveAsync(String)` and sends only `PREPEND`, `INSERT_NEXT`, `APPEND`, or `INSERT_AND_PLAY` after preview resolves.
- Produces `WorldUiAddMediaModel.setInput(String)`, `preview()`, `canConfirm()` and `confirm(AddMode)`.

- [ ] **Step 1: Write local-preview state tests**

```java
@Test
void confirmIsDisabledUntilTheLocalResolverReturnsSupportedMetadata() {
    model.setInput("https://unsupported.example/video");
    completeResolverFailure();
    assertFalse(model.canConfirm());
}
```

- [ ] **Step 2: Run focused test and verify it fails**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiAddMediaModelTest'`

Expected: FAIL because the add-media model is absent.

- [ ] **Step 3: Implement a debounced client-only resolver preview and native full-screen screen**

```java
public void confirm(AddMode mode) {
    if (!canConfirm()) return;
    controlSender.sendAdd(target, revisionAtOpen, preview.normalizedUrl(), mode);
}
```

Show title, author, description, platform and cover/fallback locally. Preserve entered URL and preview after a rejected result; close only after accepted result.

- [ ] **Step 4: Run focused test and compile every version adapter**

Run: `./gradlew :mcedia-mtv:test --tests '*WorldUiAddMediaModelTest' :mcedia-mtv-1.21.11:compileJava :mcedia-mtv-26.1:compileJava :mcedia-mtv-26.2:compileJava`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mcedia-mtv/src/client/java/top/tobyprime/mcedia_mtv/client/{worldui,metadata} mcedia-mtv/versions mcedia-mtv/src/test/java/top/tobyprime/mcedia_mtv/client/worldui
git commit -m "feat: preview and add MTV playlist media locally"
```

### Task 11: Verify compatibility, load behavior and migration

**Files:**
- Modify: `docs/superpowers/specs/2026-08-07-mtv-world-player-ui-design.md` only if implementation changes an accepted wire field or deployment requirement.
- Create: `docs/mtv-world-ui-manual-verification.md`.

- [ ] **Step 1: Add regression tests for old-server gating and old-client plugin behavior**

```java
@Test
void absentCapabilitiesLeavesLegacyChannelPlaybackUntouched() {
    assertFalse(WorldUiCapabilityState.getInstance().supported());
    legacySnapshotHandler.accept(snapshot);
    assertEquals(snapshot, ClientChannelPlaybackManager.getInstance().getSnapshot("channel"));
}
```

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew :mcedia-mtv:test :mcedia-mtv-plugin:test`

Expected: PASS.

- [ ] **Step 3: Run every supported version build**

Run: `./gradlew :mcedia-mtv-1.21.11:build :mcedia-mtv-26.1:build :mcedia-mtv-26.2:build :mcedia-mtv-plugin:build`

Expected: PASS.

- [ ] **Step 4: Perform and record manual integration checks**

Record a checklist covering new/new, new/old and old/new client-plugin pairs; large playlists; stale revisions; blocked screen; forged target data; drag bandwidth; container GUI coexistence; cover failure; and all supported versions.

- [ ] **Step 5: Commit**

```bash
git add docs mcedia-mtv/src/test mcedia-mtv-plugin/src/test
git commit -m "test: verify MTV world UI compatibility"
```

## Self-Review

- Spec coverage: Tasks 1-3 cover bounded protocol, capability gating, pages and local metadata; Tasks 4-7 cover publisher, server authority, rate limiting, UUID/world/occlusion validation and watches; Tasks 8-10 cover collapsed/expanded world controls, local drag, version adapters, queue operations and add-media preview; Task 11 covers compatibility and bandwidth/manual acceptance.
- Placeholder scan: each task names source/test locations, an interface, a failing test, a command, implementation behavior and a concrete commit command.
- Type consistency: all client/server payload fields originate from Task 1 records; all later controls use `WorldUiControlRequest` and `WorldUiControlResult`; plugin pages use `WorldUiPlaylistManifest` and `WorldUiPlaylistPage`.
