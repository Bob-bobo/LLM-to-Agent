# 第七章的Agent使用方式
from dotenv import load_dotenv
from hello_agents import SimpleAgent, HelloAgentsLLM

load_dotenv(dotenv_path="../.env")

agent = SimpleAgent(name="学习助手", llm=HelloAgentsLLM())

# 第一次对话
response1 = agent.run("还记得我的名字吗？")
print(response1)  # "很好！Python基础语法是编程的重要基础..."

# 第二次对话（新的会话）
response2 = agent.run("你还记得我的学习进度吗？")
print(response2)  # "抱歉，我不知道您的学习进度..."