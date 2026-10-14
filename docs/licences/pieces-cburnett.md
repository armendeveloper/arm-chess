# Piece artwork: Cburnett set, BSD 3-Clause

- **What:** the 12 chess-piece drawables `app/src/main/res/drawable/piece_{w,b}{k,q,r,b,n,p}.xml` and the launcher icon layers `ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml` (derived from the black knight).
- **Author:** Colin M.L. Burnett, Wikimedia user [Cburnett](https://commons.wikimedia.org/wiki/User:Cburnett).
- **Source:** Wikimedia Commons, the "t45" set (`File:Chess_{k,q,r,b,n,p}{l,d}t45.svg`). **Not** taken from the Lichess repository, which redistributes the same set under GPLv2+ only (design 02 §6.3).
- **Licence chosen:** BSD 3-Clause. Each file page offers a choice of GFDL 1.2+ (with the CC BY-SA 3.0 relicence migration), BSD, or GPL 2+; we take the BSD option. On Commons, `{{BSD}}` without a `version` parameter is the 3-clause licence (its template defaults to `version=3`).
- **Downloaded and checked:** 2026-10-14, by the T-13 build (task in `docs/product/chess-vs-computer/04-plan.md`). All 12 pages were checked, not a sample.

## Attribution the app must show

S7 About (string `about_pieces`, design 02 §4.10):

> Chess pieces by Colin M.L. Burnett (Cburnett), used under the BSD 3-clause licence.

S7b Licences must show the full notice in [Licence text](#licence-text-bsd-3-clause) below. Clause 2 asks for the notice "in the documentation and/or other materials provided with the distribution", and the app ships as a binary, so the full text has to be inside the app, not only in this repository. The name "Colin M.L. Burnett" is the form the author asks for when his images are used outside Wikimedia projects ([en:User:Cburnett](https://en.wikipedia.org/wiki/User:Cburnett), "Images" section). Clause 3: don't use his name to endorse or promote the app (store listing included).

## How each file was verified

For each of the 12 pages we read the current wikitext, the full description-page history and the upload history through the Commons API (`action=query&prop=revisions|imageinfo`). Findings:

1. **All 12 current pages offer BSD.** Each has the licence block `{{self|GFDL|migration=relicense|BSD|GPL}}` and `author={{U|Cburnett}}`.
2. **The BSD option was added by the author himself.** On each page, Cburnett's original upload (2006-12-28) was `{{self|GFDL}}`. Cburnett then edited every page on 2007-01-12 with the summary "Dual license with BSD", and on 2007-12-12 with "Added GPL". The current `{{self|…}}` form is a 2009 bot migration (MGA73bot2) of that same licence choice; it removed nothing.
3. **Vandalism checked.** `Chess_rdt45.svg` had an IP edit on 2023-11-16 that added junk text to the licence template. It was reverted, and the current revision is clean.
4. **Revision used: the author's own last upload (2007-01-03, "Updated piece").** Every file was later re-uploaded by other Commons users (Antonsusi 2010/2012, TomFryers 2020/2021, Cyco130 2021, Apathor 2021, Edemaine 2022, Smasongarrison 2022). Those changes were code cleanup, symmetry fixes and alignment tweaks. To keep the provenance unambiguous (every byte authored by Cburnett, who granted BSD), we converted Cburnett's own final revision of each file, not the current one. We rendered both versions at 24, 72, 96 and 288 px on light and dark squares and compared them. The differences are cosmetic: slightly different band curves on the black king and queen, 1 px rather than 1.5 px outlines on some black pieces, and sub-pixel asymmetries. **If the current revisions are preferred later,** they are listed below with their SHA-1, and swapping is a re-conversion of the same 12 files; the licence analysis would then need to cover the later uploaders' edits.

| Drawable | File page | Author (page) | Licence options on page (2026-10-14) | BSD added by | Revision used (Cburnett) | SHA-1 of revision used |
|---|---|---|---|---|---|---|
| `piece_wk` | [File:Chess_klt45.svg](https://commons.wikimedia.org/wiki/File:Chess_klt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:18:26Z | 2007-01-03T04:27:03Z | `3e10903ac5f0a37584c1b1a383abcabeac588276` |
| `piece_wq` | [File:Chess_qlt45.svg](https://commons.wikimedia.org/wiki/File:Chess_qlt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:19:28Z | 2007-01-03T04:28:54Z | `17a554bd078c697b0d4685a3740aa9e9e4092251` |
| `piece_wr` | [File:Chess_rlt45.svg](https://commons.wikimedia.org/wiki/File:Chess_rlt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:19:33Z | 2007-01-03T04:29:28Z | `62121a4770d82a4093254a0efdf3304cfaebb087` |
| `piece_wb` | [File:Chess_blt45.svg](https://commons.wikimedia.org/wiki/File:Chess_blt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:18:19Z | 2007-01-03T04:26:33Z | `ce7ba142a35dd3ca6b46ffa54cf8f1026d2a22d8` |
| `piece_wn` | [File:Chess_nlt45.svg](https://commons.wikimedia.org/wiki/File:Chess_nlt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:18:36Z | 2007-01-03T04:27:50Z | `a7660e97869c127ac2b8103e61a5332a44800c25` |
| `piece_wp` | [File:Chess_plt45.svg](https://commons.wikimedia.org/wiki/File:Chess_plt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:18:43Z | 2007-01-03T04:28:21Z | `1325d83948cef24dba779e080648135e04d99745` |
| `piece_bk` | [File:Chess_kdt45.svg](https://commons.wikimedia.org/wiki/File:Chess_kdt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:18:21Z | 2007-01-03T04:26:49Z | `bcb92de2513541f6fb1b51605b59d91799d9ca7b` |
| `piece_bq` | [File:Chess_qdt45.svg](https://commons.wikimedia.org/wiki/File:Chess_qdt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:19:21Z | 2007-01-03T04:28:38Z | `a1082cde5c86104d8d5f0e4398439b8da9ec086c` |
| `piece_br` | [File:Chess_rdt45.svg](https://commons.wikimedia.org/wiki/File:Chess_rdt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:19:33Z | 2007-01-03T04:29:12Z | `b66a2d4a3e36acda44ea1acc99f90ca19dac1d99` |
| `piece_bb` | [File:Chess_bdt45.svg](https://commons.wikimedia.org/wiki/File:Chess_bdt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:18:11Z | 2007-01-03T04:26:12Z | `5d3598addb9da11011f5e3c85361a6b10805ee16` |
| `piece_bn` | [File:Chess_ndt45.svg](https://commons.wikimedia.org/wiki/File:Chess_ndt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:18:30Z | 2007-01-03T04:27:37Z | `d4c45f4450229c38a56578e84563475a73f6b1ef` |
| `piece_bp` | [File:Chess_pdt45.svg](https://commons.wikimedia.org/wiki/File:Chess_pdt45.svg) | Cburnett | GFDL (+CC BY-SA 3.0 migration), BSD, GPL | Cburnett, 2007-01-12T04:18:38Z | 2007-01-03T04:28:07Z | `6d097283a843b065980f802c8ed64efce27edc8e` |

The SHA-1 values are the ones Commons reports in the file history (`imageinfo.sha1`), and each downloaded file matched.

### Download URLs of the revisions used

Commons serves superseded revisions from its archive path. The `YYYYMMDDhhmmss!` prefix is when that revision was replaced, not when it was uploaded.

| File | Archive URL |
|---|---|
| Chess_klt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/4/42/20101006015721%21Chess_klt45.svg |
| Chess_qlt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/1/15/20101006015920%21Chess_qlt45.svg |
| Chess_rlt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/7/72/20101006020544%21Chess_rlt45.svg |
| Chess_blt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/b/b1/20101006020239%21Chess_blt45.svg |
| Chess_nlt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/7/70/20101006020054%21Chess_nlt45.svg |
| Chess_plt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/4/45/20101006020358%21Chess_plt45.svg |
| Chess_kdt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/f/f0/20101006015832%21Chess_kdt45.svg |
| Chess_qdt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/4/47/20101006020010%21Chess_qdt45.svg |
| Chess_rdt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/f/ff/20101006020629%21Chess_rdt45.svg |
| Chess_bdt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/9/98/20101006020321%21Chess_bdt45.svg |
| Chess_ndt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/e/ef/20101006020140%21Chess_ndt45.svg |
| Chess_pdt45.svg | https://upload.wikimedia.org/wikipedia/commons/archive/c/c7/20101006020440%21Chess_pdt45.svg |

### Current revisions (not used), for reference

| File | Current revision | Uploaded by | SHA-1 |
|---|---|---|---|
| Chess_klt45.svg | 2022-09-11T23:42:28Z | Smasongarrison | `2c7569b837971207e40f7148e2b2086aaf8e4bbd` |
| Chess_qlt45.svg | 2021-04-26T22:48:20Z | TomFryers | `e638eb28ec25007b9f8fac8476bdf6ae1fc5a0ee` |
| Chess_rlt45.svg | 2021-12-13T19:16:41Z | Apathor | `126b7779885b87e87acc713474587732711bc8d3` |
| Chess_blt45.svg | 2022-07-28T15:24:56Z | Edemaine | `35dd477dc22636bfcc559a01f75b00369205ffda` |
| Chess_nlt45.svg | 2021-12-13T19:17:11Z | Apathor | `3a2253429c0e39863b3f5ecf447209dccecdc337` |
| Chess_plt45.svg | 2020-12-11T12:45:42Z | TomFryers | `09d59e2770fcee23722ac53b26e875f76d2c1eb1` |
| Chess_kdt45.svg | 2020-12-10T11:39:11Z | TomFryers | `b1165ef85a3df6f1af2549ab0af78ab21b540e8a` |
| Chess_qdt45.svg | 2021-04-26T22:46:36Z | TomFryers | `ed72e75b7bdbf880a3c9bee053c8786cfab8bdb9` |
| Chess_rdt45.svg | 2021-12-13T19:15:55Z | Apathor | `bd0e866f1e6da8e3d6f9c0b356b60fc58391aff6` |
| Chess_bdt45.svg | 2021-12-13T19:07:28Z | Apathor | `da6dd1b5ef629bacebbd2ce26c7d81ba8a205587` |
| Chess_ndt45.svg | 2021-12-13T19:15:07Z | Apathor | `3c79cbbda76bcf1d4e4062cef6dd3b58a250a562` |
| Chess_pdt45.svg | 2020-12-11T12:44:02Z | TomFryers | `0a6d2f3dc6327a02ca591bc489d701fbff228138` |

## Conversion

- Downloaded into an empty temporary directory outside the repository. Each file was checked against its Commons SHA-1 and scanned for active content (`<script>`, `href`, `<image>`, `foreignObject`, `url(`); none was found.
- Converted with `com.android.ide.common.vectordrawable.Svg2Vector` from `com.android.tools:sdk-common:32.4.1` (the AGP 9.4.1 toolchain). This is the converter behind Android Studio's **New > Vector Asset > Local SVG**. It ran headless from a one-off script kept outside the repository, and it reported no warnings for any of the 12 files.
- The converter output is unchanged except for a provenance and licence comment at the top of each `piece_*.xml`. Every file is a 45 × 45 dp `VectorDrawable` with a 45 × 45 viewport.
- Checked visually by rendering through Robolectric native graphics at 24, 72, 96 and 288 px, on `board.square.light` (#F0D9B5) and `board.square.dark` (#8C6A4E). The black pieces' white detail lines survive at 96 px and above. At 24 px the pieces stay distinguishable.
- The launcher icon is our own derivative of `Chess_ndt45.svg` (same BSD terms):
  - **Foreground:** the black knight, scaled 1.4× inside the 66 dp safe zone, on `primaryContainer` (#FFDCC3).
  - **Monochrome:** a filled silhouette, with the eye, nostril and mane cut out as even-odd holes.

## Licence text (BSD 3-Clause)

This is the notice to show in S7b. The Commons `{{BSD}}` template (3-clause, as rendered on each file page) reads "Copyright © The author". The author, per each page's `author` field, is Colin M.L. Burnett (Cburnett):

```
Copyright © Colin M.L. Burnett (Cburnett)

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
3. Neither the name of Colin M.L. Burnett nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY COLIN M.L. BURNETT "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COLIN M.L. BURNETT BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## Fallbacks (not used)

Design 02 §6.3 lists two fallbacks: "chessnut" by Alexis Luengas (Apache-2.0) and "rhosgfx" (CC0 1.0). They were not needed, because all 12 Cburnett files are offered under BSD by their author.
