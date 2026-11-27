# main.py의 Pydantic 요청/응답 모델을 옮겨 옴. 회원 등록용 UserCreate 추가
"""API 요청/응답 모델."""
from datetime import date, datetime
from typing import Literal, Optional

from pydantic import BaseModel, Field

# 앱 RunningService.getFinalEnvironment()가 보내는 러닝 환경 (앱 EnvironmentFeature 상수와 같음)
Environment = Literal["CITY", "PARK", "FOREST", "RIVER", "LAKE"]


class UserCreate(BaseModel):
    # 앱 SignUpActivity 규칙과 같음: 닉네임 2~10자
    user_id: str = Field(min_length=2, max_length=10)
    mail: str = Field(min_length=3, max_length=255, pattern=r"^[^@\s]+@[^@\s]+$")


class RunCreate(BaseModel):
    user_id: str
    run_date: Optional[int] = None  # 러닝 시작 시각 (epoch ms). 없으면 서버가 받은 시각
    environment: Optional[Environment] = None  # 러닝 환경. 없으면 장소 캐릭터 판단 안 함
    distance: float = Field(ge=0)
    duration: float = Field(ge=0)
    pace: Optional[float] = None
    avg_speed: Optional[float] = None
    max_speed: Optional[float] = None


class Character(BaseModel):
    character_id: int
    name: str
    emoji: str
    category: str
    condition: str
    hint: str
    description: str
    required_distance: Optional[int] = None
    image_url: Optional[str] = None


class OwnedCharacter(Character):
    level: int
    exp: int
    is_main: bool
    acquired_at: datetime


class Run(RunCreate):
    running_number: int
    run_date: int
    new_characters: list[Character] = []


class Stats(BaseModel):
    user_id: str
    run_count: int
    total_distance: float
    total_duration: float
    main_character: Optional[OwnedCharacter] = None
    next_character: Optional[Character] = None


class DayDistance(BaseModel):
    day: date
    distance: float


class ReactionCreate(BaseModel):
    user_id: str  # 반응을 누른 사람
    emoji: str = Field(min_length=1, max_length=32)


class CheerCreate(BaseModel):
    user_id: str  # 응원을 보낸 사람


class ReactionCount(BaseModel):
    emoji: str
    count: int
    mine: bool  # user_id가 이 이모지를 눌렀는지 (칩 하늘색 표시용)


class RunReactions(BaseModel):
    running_number: int
    reactions: list[ReactionCount]
    cheer_count: int
    cheered_by_me: bool
