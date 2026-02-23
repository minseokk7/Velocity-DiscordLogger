<div align="center">

# Velocity Discord Logger

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.10-green?style=for-the-badge&logo=minecraft)
![Velocity](https://img.shields.io/badge/Platform-Velocity-0066CC?style=for-the-badge&logo=velocity&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

[한국어](README.md) | [English](README_EN.md)

</div>

---

**VelocityDiscordLogger**는 Minecraft Velocity 프록시 서버를 위한 **강력하고 현대적인 디스코드 로깅 플러그인**입니다.  
DiscordSRV 없이도 네트워크 전체의 상태를 완벽하게 모니터링하고, 아름다운 Embed 메시지로 알림을 받아보세요.

## ✨ 주요 기능

### 📡 네트워크 전체 모니터링
- **접속/퇴장 로그**: 플레이어가 네트워크에 들어오거나 나갈 때 깔끔한 Embed 메시지로 기록합니다.
- **서버 이동 무시**: 불필요한 스팸을 줄이기 위해 서버 간 이동 로그는 제외할 수 있습니다.

### 🔔 서버 상태 알림 (강력함!)
- **시작/종료 알림**: 프록시 서버가 켜지거나 꺼질 때 공지사항 채널에 알림을 보냅니다.
- **🛡️ 강제 종료 방어**: `docker restart`나 예기치 않은 강제 종료 시에도 **JVM Shutdown Hook**과 **REST API**를 통해 마지막 종료 알림을 확실하게 보냅니다. (놓치는 알림 제로!)

### 💻 실시간 콘솔 미러링
- **콘솔 로그 전송**: Velocity 서버의 콘솔 로그를 디스코드 채널에서 실시간으로 확인하세요.

### 🔗 백엔드 서버 연동 (Paper/Purpur)
- **🏆 도전과제**: 플레이어가 발전 과제를 달성하면 디스코드에 자랑합니다.
- **☠️ 사망 메시지**: 플레이어가 죽으면 사망 원인을 디스코드에 기록합니다.
- *(별도의 백엔드 플러그인 설치 필요)*

---

## 📥 설치 방법

1. [Releases](https://github.com/minseok7891/VelocityDiscordLogger/releases) 탭에서 최신 버전을 다운로드하세요.
2. `VelocityDiscordLogger-1.0.2.jar` 파일을 Velocity 서버의 `plugins` 폴더에 넣습니다.
3. 서버를 재시작하여 설정 파일을 생성합니다.
4. `plugins/velocitydiscordlogger/config.toml` 파일을 열어 설정을 완료합니다.

---

## ⚙️ 설정 가이드 (`config.toml`)

```toml
# ==========================================
#        VelocityDiscordLogger 설정
# ==========================================

# 1. 디스코드 봇 토큰 (필수)
bot_token = "여기에_봇_토큰을_입력하세요"

# 2. 채널 ID 설정
[channels]
log = "123456789012345678"          # 📝 접속/퇴장 로그
status = "123456789012345678"       # 📢 서버 시작/종료 알림
console = "123456789012345678"      # 💻 콘솔 로그
achievements = "123456789012345678" # 🏆 도전과제 로그
deaths = "123456789012345678"       # ☠ 사망 로그

# 3. 메시지 커스터마이징
[messages.join]
enabled = true
color = "#00ff00" # 초록색
format = "%username% 님이 네트워크에 접속했습니다."

[messages.quit]
enabled = true
color = "#ff0000" # 빨간색
format = "%username% 님이 네트워크를 떠났습니다."

[messages.start]
enabled = true
color = "#00ff00"
format = ":white_check_mark: **서버가 시작되었습니다!**"

[messages.stop]
enabled = true
color = "#ff0000"
format = ":octagonal_sign: **서버가 종료되었습니다.**"

# 4. 아바타 설정 (Visage API)
[avatar]
base_url = "https://visage.surgeplay.com/face/96/{uuid}"
```

---

## 🧩 백엔드 플러그인 설치

**도전과제**와 **사망 메시지** 기능을 사용하려면, 각 백엔드 서버(Lobby, Survival 등)에 **[VelocityDiscordLogger-Backend](https://github.com/minseok7891/VelocityDiscordLogger-Backend)** 플러그인을 추가로 설치해야 합니다.

---

<div align="center">
  Made with ❤️ by minseok
</div>
