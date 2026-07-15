import subprocess

commits = [
    ("bd3a395", "init spring boot project"),
    ("fff4532", "define core engine interfaces"),
    ("fd0228a", "add architecture docs"),
    ("97489be", "implement lru list and ttl heap"),
    ("18963c1", "remove spring dependencies"),
    ("f9460f2", "delete spring files, convert to pure java"),
    ("0e230f2", "add thread-safe put method"),
    ("14b40d0", "add thread-safe get with lazy expiry"),
    ("9a863ac", "make remove thread-safe"),
    ("b0a459c", "add thread-safe clear"),
    ("cf31116", "add reapExpired helper for background thread"),
    ("6182583", "add background ttl reaper daemon"),
    ("a5f12b9", "add comprehensive test suite"),
    ("a9f3093", "clean up comments"),
    ("54fc773", "drop eviction policy interface and simplify put"),
    ("f1a709c", "clean up test comments"),
]

def run(cmd):
    subprocess.run(cmd, shell=True, check=True)

try:
    run("git branch backup-main")
    
    # Start fresh from the first commit (but we actually want to replace the first commit too, 
    # but the first commit has no parent. It's easiest to create an orphan branch or just 
    # start from the first commit and amend it).
    
    run("git checkout --orphan rewrite-main")
    run("git rm -rf .")
    
    # Cherry pick the first commit
    run(f"git cherry-pick {commits[0][0]}")
    run(f"git commit --amend -m \"{commits[0][1]}\"")
    
    for h, m in commits[1:]:
        run(f"git cherry-pick {h}")
        run(f"git commit --amend -m \"{m}\"")
        
    run("git branch -D main")
    run("git branch -m rewrite-main main")
    print("SUCCESS")
except Exception as e:
    print(f"FAILED: {e}")
