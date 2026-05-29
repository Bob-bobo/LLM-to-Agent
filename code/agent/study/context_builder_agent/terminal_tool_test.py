from hello_agents.tools import TerminalTool

if __name__=="__main__":
    terminal = TerminalTool(workspace="./project_notes")
    print(terminal.run({"command": "dir"}))

