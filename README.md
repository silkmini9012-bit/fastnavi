# 빠른길 내비 (FastNavi)

출발지·도착지를 넣으면 **티맵 / 네이버지도 / 카카오맵**의 자동차 경로를 동시에 조회해서
도착 예정 시각, 소요시간, 거리, **통행료(유료/무료)**를 비교하고, 가장 빨리 도착하는 앱으로 바로 길안내를 실행하는 안드로이드 앱입니다.

## 동작 방식

세 앱은 앱 안의 예상 도착시간을 다른 앱에 알려주지 않습니다. 그래서 각 회사가 공개한 **공식 경로 API**(앱과 같은 경로 엔진)로 예상 시간을 받아와 비교하고,
고른 앱은 **URL 스킴**으로 실행합니다.

| 앱 | 시간·통행료 조회 API | 실행 방식 |
|---|---|---|
| 티맵 | SK open API `tmap/routes` | `tmap://route?goalx=&goaly=&goalname=` → 바로 안내 시작 |
| 네이버지도 | 네이버 클라우드 Maps Directions 5 | `nmap://navigation?dlat=&dlng=` → 바로 안내 시작 |
| 카카오맵 | 카카오모빌리티 길찾기 API | `kakaomap://route?ep=&by=CAR` → 길찾기 화면 (‘안내 시작’ 1회 터치) |

- 출발지를 "현재 위치"가 아닌 다른 곳으로 지정하면 비교는 그 지점 기준으로 하고, 앱 실행 시에는 네이버/카카오는 지정 출발지 경로 화면을, 티맵은 현재 위치 기준 안내를 띄웁니다.
- "무료도로" 비교: 티맵 `searchOption=1(무료우선)`, 네이버 `traavoidtoll`, 카카오 `avoid=toll`로 한 번 더 조회합니다. 앱 실행 시 URL 스킴으로는 무료도로 옵션을 넘길 수 없어, 무료도로 기준일 때는 앱에서 직접 옵션을 고르라는 안내가 나옵니다.

## 화면 기능

- 출발/도착 장소 검색(카카오 로컬, 없으면 TMAP POI), 현재 위치, 최근 목적지 5개, 출발↔도착 바꾸기
- 비교 결과: 가장 빠른 앱 카드(도착시각, 2등과의 차이) + 앱별 카드
  - 기본 경로 / 무료도로 경로 각각 소요시간·도착시각·거리·통행료 배지(유료 금액 / 무료)
  - "통행료 2,300원 내면 7분 빨라요" 같은 요약
  - 네이버는 유류비·택시비, 티맵·카카오는 택시비도 표시
- 기준 전환: "가장 빠른 길" ↔ "무료도로만"
- 설정의 "가장 빠른 앱 자동 실행"을 켜면 비교 후 3초 카운트다운 뒤 1등 앱이 자동 실행(취소 가능)

## API 키 발급 (설정 화면에 입력)

1. **TMAP 앱키**: SK open API(openapi.sk.com) 가입 → TMAP API 상품 사용 신청 → 앱키 발급.
2. **네이버 클라우드**: ncloud.com 가입(결제수단 등록 필요) → Console → Maps → Application 등록 → **Directions 5** 체크 → Client ID / Client Secret.
   (Directions 5에 체크하지 않으면 429 오류가 납니다.)
3. **카카오 REST API 키**: developers.kakao.com → 내 애플리케이션 추가 → 앱 키의 REST API 키.
   [카카오맵] 사용 설정을 켜야 장소 검색이 됩니다. 같은 키로 카카오모빌리티 길찾기도 호출합니다.

키가 없는 앱은 비교에서 빠지고, 나머지 앱끼리만 비교합니다. 개인적으로 쓰는 범위면 각 사 무료 사용량 안에서 충분하지만,
정확한 무료 한도와 요금은 각 콘솔에서 확인하세요. (카카오모빌리티는 비상업적 용도이면 무료 쿼터 안에서 별도 제휴 없이 쓸 수 있습니다.)

## 빌드

1. Android Studio(최신)에서 `FastNavi` 폴더 열기 → Gradle 동기화
2. 휴대폰 연결 후 ▶ Run, 또는 Build → Build APK(s)
- minSdk 26 (Android 8.0+), Kotlin + Jetpack Compose, 외부 라이브러리는 OkHttp 하나

## 파일 구조

```
app/src/main/java/kr/fastnavi/app/
├─ MainActivity.kt      화면(메인 비교 / 장소 검색 / 설정)
├─ MainViewModel.kt     비교 로직, 가장 빠른 앱 선택, 자동 실행
├─ NavLauncher.kt       세 앱 URL 스킴 실행, 설치 확인
├─ LocationHelper.kt    현재 위치
├─ Prefs.kt             API 키·옵션·최근 목적지 저장
├─ Models.kt, Format.kt
└─ api/
   ├─ RouteApis.kt      TMAP / 네이버 / 카카오 경로 API
   ├─ PlaceSearch.kt    장소 검색
   └─ Http.kt
```

## 참고·한계

- 비교 값은 API 조회 시점의 예상치입니다. 앱을 실행하면 각 앱이 다시 탐색하므로 1~2분 정도 차이가 날 수 있습니다.
- 티맵 URL 스킴은 공식 문서가 없어 널리 쓰이는 `tmap://route?goalname=&goalx=&goaly=` 형식을 사용했습니다. 앞으로 티맵이 바뀌면 `NavLauncher.kt`만 고치면 됩니다.
- API 키는 기기 내부 저장소(SharedPreferences)에만 보관합니다. 개인용 앱이 전제입니다.
