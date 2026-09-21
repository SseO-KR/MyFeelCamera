# 03. Rendering & LUT

이 문서는 카메라 프레임(OES 텍스처)이 화면에 그려지고 촬영 결과에 저장되기까지, LUT 기반 필름 색보정이 어디서 어떻게 적용되는지를 다룬다. Layout mode 판정 → `04-adaptive-layout.md`. Surface/EGL 재생성 이벤트와 정확한 소유권 전이 → `05-lifecycle-and-resource-ownership.md`. 구현 순서 → `06-implementation-roadmap.md`.

## 계층 구조

이 문서는 다음 5개 계층으로 조직한다. 아래로 내려갈수록 구현 세부사항이고, **하위 계층의 사실은 상위 계층의 요구사항으로 역승격하지 않는다.**

```
Product Requirement
    ↓
Film Preset / Selected Film
    ↓
Resource Contract
    ↓
Rendering Contract
    ↓
Current Shader Implementation
```

예: "지금 셰이더가 Preview/Capture 두 파일로 나뉘어 있다"는 Current Shader Implementation의 사실이다. 이 사실이 "아키텍처가 항상 두 파일이어야 한다"를 의미하지 않는다.

각 결정에는 다음 표기를 병기한다: **(Product Requirement)** · **(Resource Contract)** · **(Android/OpenGL API Contract)** · **(Verify First)** · **(Later)**.

## 1. Product Requirement

- Preview와 Capture는 시각적으로 최대한 일치해야 하고, 같은 source of truth(선택된 Film)에서 나와야 한다. **(`00` §1, §10)**
- 정확히 2개의 Film Preset(Warm, Dusk)을 제공하고, 촬영 전 선택·변경할 수 있다. **(`00` §3)**
- **P0의 필수 효과는 LUT 색보정으로 한정한다.** Grain/Vignette는 §5(Current Shader Implementation)에 이미 구현돼 있지만, 셰이더에 존재한다는 사실이 제품 채택을 의미하지 않는다 — 채택 여부는 Later/Verify First다. **(`00` §3, §5 — 이번 지시로 명시)**

## 2. Film Preset / Selected Film

기존에 합의한 구조를 그대로 유지한다:

```
FilmPreset 목록
      ↓
Selected Film (단일 source of truth)
      ├─ Preview
      └─ Capture
```

- 지금 2개 Preset이 각각 정확히 하나의 LUT 리소스에 대응한다는 것이 실물로 확인됐다 — `film_warm.bin`, `film_dusk.bin`. **(Product Requirement와 Resource가 일치함을 확인)**
- Kotlin 타입/필드는 지금 확정하지 않는다. **(`01` §8)**

## 3. Resource Contract

LUT `.bin` 파일 자체에서 직접 검증한 사실이다. 이 데이터를 어떤 셰이더가 소비하든 변하지 않는다.

- Dimension: 32 × 32 × 32 **(Resource Contract)**
- Channel: RGB (3 components) **(Resource Contract)**
- Component: 1 byte **(Resource Contract)**
- `film_warm.bin`, `film_dusk.bin` 모두 이 계약을 동일하게 만족한다(byte size 98,304로 동일, 내부 구조 동일). **(Resource Contract)**

이 계약은 "이 데이터가 GPU에 어떻게 올라가는가"에 대해 아무것도 말하지 않는다.

## 4. Rendering Contract

Resource Contract를 실시간 GPU 렌더링에 쓰려 할 때 일반적으로 성립하는 사실이다. 아직 특정 셰이더나 텍스처 타깃을 전제하지 않는다.

- 1 byte/component라는 관측 사실(Resource Contract)과, 그 바이트를 unsigned normalized RGB8로 해석해 GPU에 올리는 것은 서로 다른 결정이다. 후자를 택하면 셰이더는 이 값을 `[0.0, 1.0]` 부동소수로 샘플링한다. **(Android/OpenGL API Contract)**
- 프레임마다 하드웨어 보간과 함께 이 LUT를 반복 샘플링하려면 텍스처 객체로 올려야 한다 — 매 프레임 CPU에서 순회하는 방식은 실시간 Preview 요구사항(`00` §1)과 맞지 않는다. **(Android/OpenGL API Contract — Product Requirement에서 도출)**
- 이걸 3D 텍스처로 올릴지, 2D atlas로 펼쳐 셰이더에서 직접 슬라이스 보간을 할지는 여기서 정하지 않는다 — 그건 이 텍스처를 소비하는 셰이더의 선택이다(§5).

## 5. Current Shader Implementation (제공된 shader를 그대로 쓸 경우)

아래는 전부 "지금 준 shader 파일들을 그대로 쓴다"는 조건에서만 성립한다. **이 중 어느 것도 architecture invariant로 승격하지 않는다** — 다른 shader/파이프라인을 쓰기로 하면 전부 다시 판단 대상이다.

- LUT는 `GL_TEXTURE_3D`로 업로드해야 한다 — `uniform sampler3D uLut;` + `texture(uLut, vec3)` 호출이 요구한다. LUT binary나 Rendering Contract가 강제하는 게 아니라 이 shader의 선택이다.
- `applyFilm()`이 `color.rgb`를 그대로 3D 좌표로 쓰므로, 바이트 상 가장 빠르게 변하는 축을 텍스처 업로드 시 width(가장 빠른 축)에 대응시키고 그 축을 `color.r`로 읽어야 한다. 다른 채널-축 대응도 이론적으로 가능하지만, 그러려면 이 셰이더 대신 다른 좌표 구성이 필요하다.
- LUT 크기는 셰이더 소스에 `const float LUT_SIZE = 32.0`으로 컴파일돼 있다 — 런타임에 다른 크기 LUT를 쓰려면 이 상수도 같이 바꿔야 하고, 안 바꾸면 조용히 잘못된 샘플링이 나온다.
- LUT 샘플링은 half-texel-center 공식(`lutScale=(32-1)/32`, `lutOffset=0.5/32`)을 쓴다 — 색상 0.0/1.0이 텍셀 0/31의 중심에 정확히 오도록 하는 정석적인 매핑이며, `GL_LINEAR` 필터링을 전제한다.
- Preview와 Capture는 지금 별도 fragment shader 파일이다(`samplerExternalOES` vs `sampler2D`). 색보정 로직(`applyFilm`)은 두 파일에 바이트 단위로 중복돼 있다.
- Grain은 `vEffectCoord` 기반의 결정론적(시간 무관) 노이즈이고, `uGrainScale`은 픽셀이 아니라 UV(0~1) 단위다. Vignette는 `uVignetteStrength`/`uVignetteColor`만 uniform으로 노출되고 falloff 반경(0.20~0.70)은 하드코딩이다.
- 이 자산이 grain/vignette 기능을 갖고 있다는 사실은 §1의 "P0는 LUT로 한정" 결정을 바꾸지 않는다.

## 6. P0 Implementation Decision

이건 새로운 계층이 아니라, 위 계층들 위에서 P0가 실제로 내리는 선택이다. **Product Requirement로 승격하지 않는다.**

- P0는 §5에 기술된 현재 제공된 shader asset을 초기 구현에 그대로 사용한다. 그 결과 P0는 LUT를 `GL_TEXTURE_3D` + `sampler3D`로 업로드·샘플링한다. **(Implementation Decision)**
- 이 결정의 근거는 §3(Resource Contract)이나 §4(Rendering Contract)가 3D 텍스처를 요구해서가 아니라, P0가 §5의 shader를 그대로 채택했기 때문이다 — §3/§4의 구분(3D 텍스처는 LUT 데이터도 Rendering Contract도 강제하지 않는다는 것)은 그대로 유지된다.
- P0는 Warm/Dusk 두 LUT를 각 EGL Context의 `onSurfaceCreated()`에서 모두 `GL_TEXTURE_3D`로 미리 업로드한다. LUT 하나는 98,304 byte이므로 두 개의 원본 payload 합계도 196,608 byte로 현재 P0 범위에서는 부담이 매우 작다. Film 선택 시마다 asset을 다시 읽거나 `glTexImage3D()`로 GPU에 재업로드하지 않고, 이미 생성된 texture ID의 binding만 바꿔 Preview가 즉시 반응하게 하기 위한 선택이다. **(Implementation Decision)**
- 이 선업로드 정책은 현재의 32³ RGB8 LUT 두 개라는 작은 규모에 한정한다. Preset 수나 LUT 해상도가 증가해 GPU 메모리 사용량 또는 초기화 시간이 의미 있게 커지면 실제 측정 결과를 바탕으로 on-demand 업로드와 cache/eviction 정책을 재검토한다. **(Later)**
- EGL Context가 재생성되면 기존 Context에 속했던 texture ID는 더 이상 사용할 수 없으므로, 두 LUT도 새 Context의 `onSurfaceCreated()`에서 다시 업로드한다. **(Android/OpenGL API Contract)**
- 2D atlas 등 다른 LUT representation을 지원하기 위한 추상화(교체 가능한 인터페이스, representation을 감추는 레이어 등)는 지금 만들지 않는다. **(Later)** 실제로 다른 representation이 필요해지는 요구사항(예: 3D 텍스처 미지원 기기 대응, 다른 출처의 LUT 포맷 도입)이 생기면 그때 재검토한다 — 지금은 그 가능성만으로 architecture를 복잡하게 만들지 않는다.

## 7. Preview / Capture 일치 — 무엇이 같아야 하고 무엇은 달라도 되는가

**A. 반드시 같아야 하는 것** — Product Requirement / Film Preset 계층에서 나온다
- Selected Film
- LUT 리소스(같은 `.bin` 파일)
- LUT 해석 규칙 — 지금 shader를 쓴다면 §5의 half-texel 샘플링 공식
- 색상 효과의 의미 — 같은 파라미터 값은 Preview와 Capture에서 같은 시각적 의미를 가져야 한다

**B. 서로 달라도 되는 것** — Current Shader Implementation 계층의 자유도
- source 텍스처 타입(OES vs 2D)
- rendering execution path(Preview·Capture가 파이프라인을 공유하는지 여부 자체가 Verify First — `01` §7)
- shader entry point(파일이 몇 개든 무관)
- Preview/Capture의 처리 시점(연속 스트림 vs 셔터 1회)

**C. 아직 검증해야 하는 것 (Verify First)** — 지난 분석에서 그대로 유지
- crop/aspect 차이가 최종 결과에 미치는 영향
- grain/vignette의 좌표계·크기 일치 (제품에 채택된다면) — `uGrainScale`이 UV 기준이라 해상도 차이에 민감
- color space / JPEG decode 과정으로 인한 색상 차이 — 셰이더 자체엔 색공간 처리가 없어, 리스크는 전적으로 두 소스 텍스처가 셰이더 진입 전에 같은 값 범위인지에 달림
- GPU precision 차이 (`precision highp float` 선언은 확인했으나 기기 간 완전 동일은 별도 확인 필요)
- Preview와 저장 결과의 육안상 일치 기준
- `LUT_SIZE` 하드코딩과 실제 LUT 파일 크기의 결합을 어떻게 관리할지
- 비네트 falloff 반경 하드코딩이 의도인지
- Grain/Vignette를 제품에 채택할지 자체 (§1)

## 8. 이 문서에서 다루지 않는 것

- Layout mode 판정 로직, Portrait 외 모드 → `04-adaptive-layout.md`
- Surface/EGL 재생성 시 정확한 이벤트 전파·소유권 전이 → `05-lifecycle-and-resource-ownership.md`
- LUT 업로드 코드, shader 로딩 순서 등 구현 순서 → `06-implementation-roadmap.md`
