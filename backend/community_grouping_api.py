from datetime import date
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from community_grouping import run_monthly_grouping

router = APIRouter()

class GroupingRequest(BaseModel):
    group_month: date


@router.post("/admin/community-groups/grouping")
def admin_run_grouping(request: GroupingRequest):
    try:
        result = run_monthly_grouping(
            request.group_month,
            "ADMIN"
        )
        return result

    except ValueError as error:
        raise HTTPException(
            status_code=400,
            detail=str(error)
        )

    except Exception as error:
        raise HTTPException(
            status_code=500,
            detail=str(error)
        )
