from hello_agents.tools import NoteTool

if __name__=="__main__":
    notes = NoteTool(workspace="./project_notes")

    # note_id = notes.run({
    #     "action": "create",
    #     "title": "我是四川彭于晏",
    #     "content": """## 完成情况
    #     已完成发型的打理
    #     已完成西装定制
    #     ## 下一步
    #     购买皮鞋
    #     """,
    #     "note_type": "task_state",
    #     "tags": ["refactoring", "phase1"]
    # })
    #
    # print(f"笔记创建完成，ID：{note_id}")

    # 查询笔记
    # result = notes.run({
    #     "action": "read",
    #     "note_id": "note_20260522_161539_0"
    # })
    # print(result)

    # 列出笔记
    results = notes.run({
        "action": "summary"
    })
    print(results)