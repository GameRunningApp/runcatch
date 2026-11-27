# routers/reactions.py의 get_run_reactions()를 옮겨 옴 (피드와 같이 씀)
"""러닝 기록 1건의 반응(이모지) · 응원 집계. 반응 API와 커뮤니티 피드가 함께 쓴다."""
from models import ReactionCount, RunReactions


def get_run_reactions(cur, running_number, user_id):
    cur.execute(
        "SELECT emoji, COUNT(*) AS count, SUM(user_id = %s) AS mine "
        "FROM run_reaction WHERE running_number = %s "
        "GROUP BY emoji ORDER BY MIN(created_at), emoji",
        (user_id, running_number),
    )
    reactions = [
        ReactionCount(emoji=row["emoji"], count=row["count"], mine=row["mine"] > 0)
        for row in cur.fetchall()
    ]
    cur.execute(
        "SELECT COUNT(*) AS count, COALESCE(SUM(user_id = %s), 0) AS mine "
        "FROM run_cheer WHERE running_number = %s",
        (user_id, running_number),
    )
    cheer = cur.fetchone()
    return RunReactions(
        running_number=running_number,
        reactions=reactions,
        cheer_count=cheer["count"],
        cheered_by_me=cheer["mine"] > 0,
    )
