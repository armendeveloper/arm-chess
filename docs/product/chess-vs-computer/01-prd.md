# PRD: Chess vs Computer (Android, Kotlin), v1

- **Status:** Draft for design
- **Author:** Product (drafted 2026-10-14)
- **Owner / sole builder:** the developer (solo project, no backend, no accounts)
- **Feature slug:** `chess-vs-computer`

---

## 1. Summary

v1 is a native Android chess app, written in Kotlin, where one person plays a full game of standard chess against a computer opponent. It runs fully offline. The app opens straight to a board, offers four difficulty levels, follows all FIDE move and draw rules, and keeps the current game safe if Android kills the app. There is no account, no network access, no ads and no data collection.

---

## 2. Problem

**What is missing.** Someone with ten spare minutes and no signal (or no wish to sign up for anything) wants a chess game against an opponent at their own level. Today they cope in one of three ways:
- They use large chess platforms that push them towards accounts, online play, upsells or ads.
- They use engine front-ends (for example DroidFish, a GPLv3 Stockfish GUI) built for analysis, which feel complex to a casual player.
- They don't play at all.

*This is an assumption. No user research has been done.*

**The bet.** A small, focused app wins a place on a casual player's phone if it does five things: opens to a board in one tap, never needs the network, plays legal chess correctly, offers an opponent that is beatable but not trivial at each level, and never loses an unfinished game.

**Evidence available**
- Prompt: the user wants "chess game on android with kotlin". v1 mode is fixed to play vs computer only.
- Code: none. The project is greenfield; `C:\armen-projects\chess-android` contains only `docs/`.
- Market (light desk research, not validation): free play-vs-computer apps already exist, including DroidFish ([gitee mirror](https://gitee.com/feng_sir/droidfish)). Competing on engine strength is not the bet; simplicity and privacy are. *Assumption.*

**Why now.** There is no external deadline. The reasons are the developer's own wish to build and ship (*assumption: partly a learning or portfolio project*) and a Play Store constraint. From 2026-08-31, new apps must target API 36 (Android 16) ([Android Developers: target SDK](https://developer.android.com/google/play/requirements/target-sdk)). Starting fresh on API 36 now avoids a migration later.

---

## 3. Users

| Persona | Job to be done | What matters to them |
|---|---|---|
| **P1: Casual player** (knows the rules, plays occasionally, rating unknown or under ~1400) | "When I have 5–20 minutes, possibly offline, I want a game against an opponent near my level so I can relax and sometimes win." | One tap to play, no sign-up, a fair opponent, resume later |
| **P2: Improving beginner** (knows how pieces move, loses to most bots) | "I want an opponent weak enough to beat, and to take back a blunder so I learn without restarting." | A truly easy level, undo, clear check and legal-move cues |

**Not for (v1):**
- Rated or competitive players who want online play, clocks, ratings or deep analysis.
- People who don't know how the pieces move. There is no tutorial.
- Two people sharing one device.
- Anyone who needs puzzles or training.

---

## 4. Goals and metrics

Constraints on measurement: there is no backend and no analytics SDK, and the app has no INTERNET permission. Every metric comes from three places only: Google Play Console (data Google collects at OS or store level from users who opted in), an on-device stats screen (US-12) that testers read out manually, and automated tests.

### Primary metric

| Metric | Definition and source | Baseline | Target |
|---|---|---|---|
| **30-day retained-installer rate** | Users who installed and still have the app 30 days later ÷ users who installed it, per monthly cohort. Source: Play Console > User acquisition > Retained installers (check the exact report name in Console). | Unknown; measure first | ≥ 25% *(assumption; no benchmark exists. Reset the target after 3 monthly cohorts of ≥ 100 installers each.)* |

**Pre-launch proxy** (Play's mandatory closed test, see Dependencies): at the end of the 14-day test, each tester reports figures from the Stats screen (US-12). Target:
- at least 9 of 12 testers have completed ≥ 3 games
- completed ÷ started is ≥ 60% across all testers

"Completed" means checkmate, stalemate, draw or resignation. A game the player leaves via New game is "abandoned", not completed.

### Guardrails (must not get worse)

| Guardrail | Source | Threshold |
|---|---|---|
| User-perceived crash rate | Play Console > Android vitals | < 1.09% overall (Play's bad-behaviour threshold); internal target ≤ 0.5% ([Android vitals](https://developer.android.com/topic/performance/vitals)) |
| User-perceived ANR rate | Play Console > Android vitals | < 0.47% overall; internal target ≤ 0.2%. The engine must never run on the main thread. |
| Rules correctness | Automated perft suite (US-4 AC) plus store reviews | 100% perft pass before every release; 0 open reports of an illegal move being allowed or a legal move being blocked |
| AI think time | Instrumented test on the reference device (OQ-6) | p95 think time per level ≤ the cap in US-6 |
| App download size | Play Console > App size | ≤ 15 MB, assuming a self-written engine (OQ-1) |

Privacy commitment, testable: the manifest declares no INTERNET permission. The Play Data safety form declares "No data collected, no data shared".

---

## 5. Scope

### MoSCoW

| Priority | Capability | Story |
|---|---|---|
| **Must** | Start a new game: pick side (White, Black or Random) and level (1–4) | US-1 |
| **Must** | Auto-save, and resume after the app is killed, the process dies or the device reboots | US-2 |
| **Must** | Make moves by tapping, with legal-move, last-move and check cues | US-3 |
| **Must** | Full FIDE move rules: castling, en passant, promotion with piece choice | US-4 |
| **Must** | Game-end detection: checkmate, stalemate, threefold repetition, 50-move rule, insufficient material | US-5 |
| **Must** | Offline computer opponent with 4 levels, off the main thread, with time caps | US-6 |
| **Must** | Resign, and abandon the current game to start a new one | US-7 |
| **Must** | Accessibility basics: TalkBack squares and moves, cues that don't rely on colour alone | US-8 |
| **Must** | Board fits phones and tablets in portrait and landscape | US-9 |
| **Should** | Undo / takeback (unlimited) | US-10 |
| **Should** | Move history list in algebraic notation | US-11 |
| **Should** | On-device stats: games started, completed, abandoned; W/D/L per level | US-12 |
| **Should** | Drag-and-drop moves (in addition to tapping) | US-13 |
| Could | Hint: highlight one suggested move (engine at level 4, ≤ 2 s) | n/a |
| Could | Move sounds and haptics, with an on/off setting | n/a |
| Could | Share the finished game as PGN through the Android share sheet (no network permission needed) | n/a |
| Could | "Always promote to queen" setting | n/a |
| Could | Rematch button (same level, same or swapped side) | n/a |
| Could | Small built-in opening book for variety at levels 3–4 | n/a |
| Could | Dark theme that follows the system setting | n/a |
| Won't (v1) | Everything in Non-goals | n/a |

### Non-goals (v1)

These are future candidates, not v1 work:
- **Two-player pass-and-play** on one device.
- **Online multiplayer**, friends, chat or matchmaking.
- **Puzzles, lessons, training** or a tutorial.
- Accounts, cloud sync, leaderboards, Play Games achievements.
- Clocks and time controls. Elo or rating display. Showing a calibrated Elo for AI levels.
- Post-game analysis, evaluation bar, review or replay mode, stepping back through history without undoing.
- Draw offers (by player or AI), AI resigning, premoves.
- Custom start positions or FEN import, Chess960 and other variants.
- More than one saved game at a time.
- Board or piece themes beyond one default set. Localisation (English only, but all strings sit in resources).
- Ads, in-app purchases, any SDK that makes network calls. Wear OS, Android TV, ChromeOS-specific work.

### Dependencies

- **Google Play developer account** (one-time fee). New personal accounts must run a closed test with **≥ 12 testers opted in for 14 consecutive days** before production access ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465)). The developer has to recruit those 12 testers. This also gives us the pre-launch metric.
- **Target SDK 36** for Play submission ([target SDK requirements](https://developer.android.com/google/play/requirements/target-sdk)).
- **Piece artwork** under a licence that allows redistribution in an app, either self-drawn or a set whose licence has been verified. Record the source and licence in `docs/`.
- **Privacy policy URL.** Play Console asks for one. A static page saying "this app collects no data" is enough; it can be hosted on a free static host. Verify current Play policy.
- **Engine licence decision** (OQ-1). Bundling Stockfish brings GPLv3 obligations.
- **Reference test device(s)** for performance caps (OQ-6).

---

## 6. User stories and acceptance criteria

Conventions:
- "Reference device" is defined in OQ-6.
- "Player" is the human; "AI" is the computer opponent.
- All times are measured on the reference device in a release build.

### US-1: Start a new game (Must)

*As P1, I want to start a game with my chosen side and level in one or two taps so that I am playing within seconds.*

- **Given** first launch, **when** the home screen appears, **then** it shows **New game** with defaults Side = White and Level = 2, and no **Continue** button.
- **Given** the New game sheet, **when** the player picks Side ∈ {White, Black, Random} and Level ∈ {1, 2, 3, 4} and taps **Play**, **then** the board appears in the standard start position within 1 s.
- **Given** the player chose Black, **when** the board appears, **then** Black is at the bottom of the board and the AI (White) makes its first move within that level's think-time cap.
- **Given** the player chose Random, **when** the game starts, **then** the side is assigned 50/50 and a message "You play White" or "You play Black" shows for ≥ 2 s. QA check: over 100 starts, each side appears between 35 and 65 times.
- **Given** a previous game was started, **when** the player opens New game again, **then** the last-used side and level are pre-selected.
- **Edge:** if a game is in progress, choosing New game asks "Abandon current game?" (Cancel / Abandon). Abandon records the game as *abandoned* in Stats (US-12) and starts the new game. Cancel returns to the current game unchanged.

### US-2: Auto-save and resume (Must)

*As P1, I want my unfinished game to still be there when I come back, even if Android closed the app, so that I never lose a game in progress.*

- **Given** a game in progress, **when** any move is applied (by the player or the AI), **then** the game state is saved to local storage within 500 ms. The state is: move list, side, level, result if any, and undo history.
- **Given** a saved unfinished game, **when** the app is relaunched after any of these, **then** the home screen shows **Continue** and tapping it restores the exact position, move history, side, level and whose turn it is:
  - swipe-away from Recents
  - "Force stop" in system settings
  - process death in the background (QA: Developer options > "Don't keep activities", or `adb shell am kill`)
  - device reboot
- **Given** the AI was thinking when the app was killed, **when** the game is restored, **then** the AI starts thinking again and moves within its cap.
- **Given** rotation, dark-mode toggle, font-size change or split-screen, **when** the configuration changes mid-game, **then** the position, any selected piece and any open promotion picker are preserved.
- **Given** the last game ended (checkmate, draw, resignation), **when** the app is relaunched, **then** **Continue** is not shown.
- **Edge (corrupt or unreadable save):** **when** the app launches, **then** it does not crash, shows "Couldn't restore your last game" once, deletes the bad save, and shows the home screen. Saves are written atomically (write a temp file, then rename) so a kill mid-write leaves the previous valid save.
- **Edge (storage full):** if the save fails, play continues and no crash occurs. A non-blocking message "Game can't be saved, storage full" appears at most once per game.

### US-3: Move pieces by tapping (Must)

*As P1, I want to tap a piece and then tap a square, with clear cues, so that I can move accurately on a small screen.*

- **Given** it is the player's turn, **when** they tap one of their own pieces that has legal moves, **then** that square is marked selected and every legal destination is marked: a dot on empty squares, a ring on capture squares.
- **Given** a piece is selected, **when** the player taps a legal destination, **then** the move is applied and the selection clears.
- **Given** a piece is selected, **when** the player taps another of their own pieces, **then** the selection moves to that piece. **When** they tap the selected piece again or any illegal square, **then** the selection clears and no move is made.
- **Given** a piece has no legal moves (pinned, blocked, or king in check), **when** it is tapped, **then** nothing is selected. A short "No legal moves" hint is allowed.
- **Given** any move has been made, **then** its from-square and to-square stay highlighted until the next move.
- **Given** a side is in check, **then** that king's square carries a check marker until the check ends.
- **Given** it is the AI's turn, **when** the player taps the board, **then** no move or selection happens, and a "Computer is thinking…" indicator is visible.
- File letters a–h and rank numbers 1–8 are drawn on the board edges and follow board orientation.

### US-4: Full move rules, including special moves (Must)

*As P1, I want the game to follow real chess rules exactly so that I can trust every result.*

- **Castling.** **Given** the king and the chosen rook have not moved, the squares between them are empty, the king is not in check, and the king does not pass through or land on an attacked square, **when** the player moves the king two squares toward the rook, **then** the game castles and moves the rook. If any condition fails, the two-square king move is not offered as a legal destination.
- **En passant.** **Given** an enemy pawn has just made a two-square advance landing beside the player's pawn, **when** the player's pawn moves diagonally behind it on the very next move, **then** the enemy pawn is captured. One move later the option no longer exists.
- **Promotion.** **Given** a pawn reaches the last rank, **when** the move is made, **then** a picker offers Queen, Rook, Bishop and Knight, and the chosen piece replaces the pawn. **When** the picker is dismissed (back gesture or tapping outside), **then** the pawn returns and no move is made. The AI may under-promote.
- **King safety.** No move that leaves the mover's own king in check is ever offered or accepted, including moves by pinned pieces and en passant captures that expose the king along a rank.
- **QA gate (automated, required for every release).** The move generator's perft node counts match published values ([Chessprogramming wiki: Perft Results](https://www.chessprogramming.org/Perft_Results)):
  - start position, depth 1–5: 20 / 400 / 8,902 / 197,281 / 4,865,609
  - "Kiwipete" position, depth 1–4: 48 / 2,039 / 97,862 / 4,085,603
  - at least 3 more positions from that page that cover castling, en passant and promotion

### US-5: Game end detection and result (Must)

*As P1, I want the game to end correctly and tell me why so that the result is never in doubt.*

- **Given** the side to move is in check and has no legal move, **then** the result is checkmate: "Checkmate. You win" or "Checkmate. Computer wins".
- **Given** the side to move is not in check and has no legal move, **then** the result is "Draw by stalemate".
- **Given** the same position occurs for the 3rd time, **then** the game ends automatically as "Draw by threefold repetition" (see OQ-3). "Same position" means: same pieces on the same squares, same side to move, same castling rights, and the same en passant capture availability. The occurrences need not be consecutive.
- **Given** 100 consecutive plies (50 moves each) have passed without a pawn move or capture, **then** the game ends automatically as "Draw by 50-move rule". If the 100th ply delivers checkmate, checkmate wins.
- **Given** the material on board is exactly one of these, **then** the game ends immediately as "Draw by insufficient material":
  - K vs K
  - K+B vs K
  - K+N vs K
  - K+B vs K+B with both bishops on the same square colour
- **When** a game ends, **then** board input stops, the board stays visible, and a result panel shows the reason with **New game** and **Home** buttons. If undo exists (US-10), it is also shown.
- The result is saved, so after a relaunch **Continue** is not offered (see US-2).

### US-6: Computer opponent with four levels (Must)

*As P2, I want to choose an opponent I can actually beat, and later a harder one, so that the game stays fun as I improve.*

Starting tuning for the levels is below. The engine developer may change any of these numbers as long as the acceptance criteria pass.

| Level | Label | Think-time cap (p95, reference device) | Starting behaviour |
|---|---|---|---|
| 1 | Beginner | 0.5 s | 1-ply search; plays a uniformly random legal move 50% of the time; often leaves pieces undefended |
| 2 | Casual | 1 s | Depth 2–3; 20% of moves chosen at random from the top 5 |
| 3 | Club | 2 s | Iterative deepening with quiescence; random pick among moves within 0.2 pawns of best |
| 4 | Strong | 4 s | Full time budget; no deliberate mistakes |

- **Given** it is the AI's turn, **then** the search runs off the main thread. While it searches, the UI stays responsive: back, resign, undo and screen rotation all work. The search causes no ANR.
- **Given** the cap is reached, **then** the AI plays the best move found so far. It never exceeds the cap by more than 200 ms.
- **Given** the AI replies faster than 400 ms, **then** its move is displayed at the 400 ms mark, so the move doesn't seem to appear before the player's own move has finished animating.
- **Given** the app goes to the background while the AI is thinking, **when** it returns, **then** the AI's move is completed (resumed or recomputed) within the cap.
- **Ordering check (automated, required before release).** In a 100-game match with alternating colours and random openings for variety, level N+1 scores ≥ 70% against level N, for N = 1, 2, 3. Draws count as 0.5. Level 4 scores ≥ 95% against level 1.
- **Basic competence (automated).** From 10 fixed K+Q vs K positions, levels 3 and 4 deliver checkmate before the 50-move rule in 10/10 games.
- **Variety.** In the same position, levels 1–3 choose different moves across 10 runs in at least 1 of the 5 test positions.
- **Calibration (closed test).** At least 70% of testers who rate their strength "beginner" report winning at least one game at level 1. This is a survey question at the end of the closed test.
- The AI only plays legal moves. It does not resign or offer draws (Non-goal).

### US-7: Resign and new game (Must)

*As P1, I want to give up a lost game quickly so that I can start a fresh one.*

- **Given** a game in progress and it is any side's turn, **when** the player taps **Resign** and confirms, **then** the AI's search stops, the game ends as "You resigned. Computer wins", and it is recorded as a loss in Stats. Cancel changes nothing.
- **Given** a game in progress, **when** the player uses system back from the board, **then** they return to Home. The game is not ended and **Continue** is shown.
- Abandoning the current game for a new one follows US-1 (edge case).

### US-8: Accessibility basics (Must)

*As a player who uses TalkBack or has a colour-vision deficiency, I want to know where every piece is and what just happened so that I can play without relying on sight or colour.*

- **Given** TalkBack is on, **when** focus lands on a square, **then** it reads the coordinate, the contents and the state, for example "e4, white knight", "d5, empty", "f7, black pawn, capture available" or "e1, white king, selected, in check".
- **Given** TalkBack is on, **when** the player double-taps one of their pieces and then double-taps a destination, **then** the move is made, the same as tap-to-move (US-3).
- **Given** any move is applied, **then** an announcement in plain words is made:
  - "You: knight g1 to f3."
  - "Computer: bishop takes knight on f3, check."
  - "Computer: queen h4 to f2, checkmate. Computer wins."
- **Given** the promotion picker or result panel opens, **then** TalkBack focus moves to it and every option has a label.
- **Non-colour cues.** No cue relies on colour alone, and each passes a check with a colour-blindness simulator (Developer options > Simulate color space: deuteranomaly, protanomaly, tritanomaly). Each cue also meets ≥ 3:1 contrast against both light and dark squares (WCAG 2.1, criterion 1.4.11):
  - selected square: a border
  - legal move: a dot
  - capture: a ring
  - check: a marker shape on the king's square
  - last move: a tint **plus** a corner mark
- **Given** system font size is 200%, **then** no button or label outside the board is clipped. All controls outside the board have touch targets ≥ 48 dp.

### US-9: Board fits any screen and orientation (Must)

*As P1 on a small phone or a tablet, I want the board fully visible and square so that I never scroll or mis-tap.*

Note: apps that target API 36 can't lock orientation on large screens, so landscape must work.

- **Given** any window from 320 dp wide (portrait phone) up to tablet landscape, **then** the board stays square and fully visible, its side length is the largest that fits, and the controls (Undo, Resign, Menu) and the move list (US-11) are visible without covering the board. Phone portrait: controls below the board. Landscape or wide: controls beside it.
- **Given** a 360 × 640 dp screen in portrait, **then** each square is ≥ 40 dp.
- **Given** a resize or rotation mid-game, **then** the state is kept (see US-2).

### US-10: Undo / takeback (Should)

*As P2, I want to take back a blunder so that I can learn without restarting the whole game.*

- **Given** it is the player's turn and at least one full move pair has been played, **when** they tap **Undo**, **then** the AI's last move and the player's last move are both reverted (2 plies), and the cues and move list update.
- **Given** the AI is thinking, **when** the player taps **Undo**, **then** the search is cancelled and the player's last move is reverted (1 ply).
- **Given** the game has ended, **when** the player taps **Undo**, **then** the game reopens at the position before their last move. The result is removed from Stats, and the game counts again as in progress.
- **Given** the player is Black and only the AI's first move has been played, **then** Undo is disabled. Undo is also disabled at the start position.
- Undo is unlimited at every level (OQ-4). Undo history survives an app kill (US-2).

### US-11: Move history (Should)

*As P1, I want to see the moves so far so that I can follow the game and spot what just happened.*

- **Given** moves have been played, **then** a list shows them in standard algebraic notation, numbered in pairs, for example "1. e4 e5 2. Nf3 Nc6". It includes "+", "#", "O-O", "O-O-O" and "=Q".
- **Given** a new move, **then** the list auto-scrolls to show it. **Given** an undo, **then** the reverted moves are removed.
- **Given** 150+ moves, **then** scrolling stays smooth (no dropped frames visible in the profiler on the reference device).
- Tapping a past move does nothing in v1 (review mode is a Non-goal).

### US-12: On-device stats (Should)

*As P1, I want to see how I'm doing at each level so that I know when to move up. (It also gives closed-test metrics without any analytics.)*

- **Given** the Stats screen is opened from Home, **then** it shows the counts below. Everything is stored only on the device.
  - games started, completed and abandoned
  - wins, draws and losses for each level 1–4
- **Given** a game ends or is abandoned, **then** the counts update immediately. Undo after a game ends reverses that result (US-10).
- **Given** a fresh install, **then** every count shows 0 and there is no empty-state error.
- **Given** **Reset stats** is tapped and confirmed, **then** all counts return to 0.

### US-13: Drag-and-drop moves (Should)

*As P1, I want to drag pieces like a physical board so that moving feels natural.*

- **Given** it is the player's turn, **when** they drag one of their pieces, **then** the piece follows the finger and the legal destinations are marked.
- **When** they release over a legal destination, **then** the move is made. **When** they release anywhere else, **then** the piece animates back and no move is made.
- Tapping (US-3) still works. A drag of less than 8 dp counts as a tap.

---

## 7. Risks

| Type | Risk | Mitigation / cheap de-risk |
|---|---|---|
| Value | Many free chess apps already offer play vs computer, so few installs or quick uninstalls | The bet is no-account, no-ads, offline, one tap to play. Test it in the closed test: ask testers "Would you keep this instead of your current chess app? Why or why not?" Accept that v1 may be partly a learning project (assumption, OQ-8). |
| Usability | Mis-taps on small phones (squares ~40–45 dp) | Tap-select-then-tap model, undo (US-10), test on a 360 dp-wide device |
| Usability | Levels badly calibrated (level 1 too strong is the most damaging case) | Calibration check in US-6; tune the parameters, not the architecture |
| Feasibility | Rules bugs in a hand-written move generator | Perft gate (US-4) plus unit tests for each draw rule, written before any UI |
| Feasibility | A Kotlin engine is too slow or weak on low-end devices | Iterative deepening with a hard deadline; caps measured on the reference device. Fallback is Stockfish (OQ-1). |
| Feasibility | ANRs from the search or from save I/O | Search on a background dispatcher with cancellation; save off the main thread; ANR guardrail |
| Viability | Bundling Stockfish obliges the app to follow GPLv3. In practice the whole app's source must be published under GPLv3 (or the engine shipped as a clearly separate program, which is legally debated), and the corresponding source must be offered alongside the binary ([GPLv3](https://www.gnu.org/licenses/gpl-3.0.html); community discussion: [TalkChess](https://talkchess.com/viewtopic.php?p=836628)). NNUE network files also add tens of MB. | Default to a self-written engine (OQ-1). If Stockfish is chosen, open-source the app under GPLv3 from day one. |
| Viability | Play publishing blockers: target API 36, the 12-tester / 14-day closed test, Data safety form, privacy policy | Recruit testers early; the closed test doubles as pre-launch validation |
| Viability | Licence on piece artwork, or trademark use (e.g. "Stockfish" or other brands in the store listing) | Use verified-licence or self-made art; describe the AI generically |
| Delivery | Solo developer, so scope creep stalls the release | Build Musts in this order: rules core with perft → engine → board UI → save/resume → accessibility. Shoulds come only after every Must passes QA. Coulds wait for v1.1. |

---

## 8. Open questions

**Status (2026-10-14): all resolved.** The user approved every recommended default below, including OQ-1 (self-written Kotlin engine). The "Recommended default" column is now the decision. OQ-6 (reference device model) is still to be recorded in the test plan.

| # | Question | Blocks | Decision (was: recommended default) |
|---|---|---|---|
| OQ-1 | **Engine:** self-written Kotlin engine or bundled Stockfish (native binary via NDK)? | Build, licence, app size | **Self-written Kotlin engine** (alpha-beta, piece-square tables, quiescence) in a pure-Kotlin module with no Android dependencies. It keeps the app small, keeps the licence free of GPL, and levels are easy to shape. Revisit Stockfish only if level 4 is called "too easy" by most closed testers; if switched, the app becomes GPLv3 open source. |
| OQ-2 | **Monetisation:** free with no ads, paid, or ads/IAP? | Data safety, SDK choice, privacy | **Free, no ads, no IAP in v1.** Any ads SDK would need network access and would break the privacy promise. Reconsider after v1 metrics. |
| OQ-3 | **Threefold repetition and 50-move rule:** automatic draw, or a "claim draw" button (strict FIDE)? | Design (result flow) | **Automatic.** No claim UI is needed, and casual players never miss a draw. Insufficient material is automatic either way. |
| OQ-4 | Should undo be limited (e.g. disabled at level 4 or capped per game)? | Design | **Unlimited at every level.** It is single-player and unrated, so no one is harmed. |
| OQ-5 | **Minimum Android version?** | Build setup | **minSdk 26 (Android 8.0)**, which reaches about 98% of active devices (third-party chart of Google's Dec-2025 data: [capgo](https://capgo.app/android-distribution-chart/); [AndroidHeadlines](https://www.androidheadlines.com/2026/01/android-version-distribution-numbers-2025-2026-market-share.html)). **targetSdk 36.** Check the Android Studio minSdk picker for current figures. |
| OQ-6 | What is the **reference device** for performance caps? | QA, US-6 caps | The lowest-end physical Android phone the developer owns (aim for ≤ 4 GB RAM, released 2020 or earlier), plus one current mid-range emulator image. Record the model in the test plan. |
| OQ-7 | Distribution channel: Play Store only, or also F-Droid / GitHub releases? | Release work | **Play Store** (needed for the vitals and retention metrics). Add F-Droid only if the app is open-sourced. |
| OQ-8 | Is v1 meant to reach many players, or mainly to ship a polished portfolio piece? | How much to invest in store listing and screenshots versus code quality | Treat it as **both**: hit the metrics above, but don't spend on marketing. |
| OQ-9 | Play "target audience" declaration: include under-13s? | Store policy | **13+ for v1.** No ads and no data make a later Families-policy declaration straightforward. |
| OQ-10 | Should hints be pulled into v1 (Could today)? | Scope | **No.** Ship without hints, then add them in v1.1 using the level-4 engine. |

---

## 9. Sources

- Android Developers: [Target API level requirements for Google Play](https://developer.android.com/google/play/requirements/target-sdk)
- Play Console Help: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- Android Developers: [Android vitals](https://developer.android.com/topic/performance/vitals); [Raising the bar on technical quality on Google Play (2022)](https://android-developers.googleblog.com/2022/10/raising-bar-on-technical-quality-on-google-play.html)
- Android version share: [capgo distribution chart](https://capgo.app/android-distribution-chart/), [AndroidHeadlines (Jan 2026)](https://www.androidheadlines.com/2026/01/android-version-distribution-numbers-2025-2026-market-share.html)
- GPLv3 text: [gnu.org](https://www.gnu.org/licenses/gpl-3.0.html). Stockfish/GPL distribution discussion: [TalkChess thread](https://talkchess.com/viewtopic.php?p=836628). Example GPLv3 Android Stockfish GUI: [DroidFish mirror](https://gitee.com/feng_sir/droidfish)
- Perft reference values: [Chessprogramming wiki](https://www.chessprogramming.org/Perft_Results)

---

## Changelog

- **2026-10-14**: First draft. v1 scope is fixed to play vs computer (the user's decision). Pass-and-play, online play and puzzles are recorded as Non-goals. 13 stories (US-1 to US-9 Must, US-10 to US-13 Should) and 10 open questions with defaults.
- **2026-10-14**: PRD approved by the user. All open questions resolved to their recommended defaults (OQ-1 self-written Kotlin engine; OQ-2 free, no ads; OQ-3 automatic draws; OQ-4 unlimited undo; OQ-5 minSdk 26 / targetSdk 36; OQ-7 Play Store only; OQ-8 both reach and portfolio; OQ-9 13+; OQ-10 hints in v1.1).
