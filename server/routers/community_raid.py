# [구조변경] 예전 위치: backend/community_raid_api.py

from fastapi import APIRouter

from services.raid import get_raid_status


router = APIRouter(
    prefix="/community-raids",
    tags=["Community Raid"],
)


@router.get("/{user_id}")
def read_community_raid(user_id: str):
    return get_raid_status(user_id)
