
from fastapi import APIRouter

from community_raid import get_raid_status


router = APIRouter(
    prefix="/community-raids",
    tags=["Community Raid"],
)


@router.get("/{user_id}")
def read_community_raid(user_id: str):
    return get_raid_status(user_id)
