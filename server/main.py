# [구조변경] 예전 backend/main.py(633줄)를 아래 파일들로 나누고, 여기에는 앱 생성 · 시작 시 초기화 · 라우터 등록만 남김
#   import만 해도 DB에 접속하던 테이블 생성을 lifespan(서버 시작 시점)으로 옮김
"""
RUN CATCH 서버. 앱 생성, 시작 시 초기화, 라우터 등록만 한다.
  config.py      설정값              db.py       DB 연결
  schema.py      테이블 생성          models.py   요청/응답 모델
  characters.py  캐릭터 데이터 (앱 RunCharacter.ALL과 맞춤)
  routers/       API                 services/   업무 규칙 (획득 판정, 그룹 배정, 레이드)
실행: server 폴더에서  venv\\Scripts\\python -m uvicorn main:app --reload
"""
from contextlib import asynccontextmanager
from datetime import date, datetime

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI

from config import KST
from routers import characters, community_grouping, community_raid, reactions, runs, users
from schema import init_schema
from services.grouping import run_monthly_grouping


def run_scheduled_grouping():
    now = datetime.now(KST)
    group_month = date(now.year, now.month, 1)

    try:
        result = run_monthly_grouping(group_month, "AUTO")
        print(f"[CommunityGroupingScheduler] {result}")
    except Exception as error:
        print(f"[CommunityGroupingScheduler] 실행 실패: {error}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # import만 해도 DB에 접속하던 것을 서버 시작 시점으로 옮김
    init_schema()

    scheduler = AsyncIOScheduler(timezone=KST)

    scheduler.add_job(
        run_scheduled_grouping,
        trigger="cron",
        day=1,
        hour=5,
        minute=0,
        id="monthly_community_grouping",
        replace_existing=True,
        max_instances=1,
        coalesce=True,
    )

    scheduler.start()
    app.state.scheduler = scheduler

    print("[CommunityGroupingScheduler] 매월 1일 05:00 자동 실행 등록 완료")

    try:
        yield
    finally:
        scheduler.shutdown(wait=False)
        print("[CommunityGroupingScheduler] 스케줄러 종료")


app = FastAPI(lifespan=lifespan)


@app.get("/")
def home():
    return {"message": "RUN CATCH backend is working!"}


app.include_router(users.router)
app.include_router(runs.router)
app.include_router(characters.router)
app.include_router(reactions.router)
app.include_router(community_grouping.router)
app.include_router(community_raid.router)
