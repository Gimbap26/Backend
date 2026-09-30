# Money Weather API 이미지. 소스에서 바로 빌드하므로 호스트에 Java/Maven 이 없어도 된다.
# x86(amd64)과 ARM(arm64, 예: 라즈베리파이) 모두 같은 파일로 빌드된다.

# --- 1단계: 빌드 ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 의존성을 먼저 받아 두면 소스만 바뀌었을 때 다시 받지 않는다(도커 레이어 캐시)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# 테스트는 배포 전에 개발 PC 에서 'mvn test' 로 돌린다(홈서버 빌드 시간을 줄이기 위해 여기서는 생략)
RUN mvn -B -q clean package -DskipTests

# --- 2단계: 실행 ---------------------------------------------------------------
FROM eclipse-temurin:21-jre

# "오늘" 날짜로 지난 이벤트·남은 일수·다가오는 결제를 판단하므로 한국 시간으로 고정한다.
# (컨테이너 기본값 UTC 면 오전 0~9시에 날짜가 하루 어긋난다)
ENV TZ=Asia/Seoul

# root 가 아닌 전용 사용자로 실행한다
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /build/target/money-weather-api-0.0.1-SNAPSHOT.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
