import os

pwd = os.path.dirname(__file__)
file0 = '../wrkdir/run/logs/replica0.log'
file1 = '../wrkdir/run/logs/replica1.log'
file2 = '../wrkdir/run/logs/replica2.log'
path0 = os.path.join(pwd, file0)
path1 = os.path.join(pwd, file1)
path2 = os.path.join(pwd, file2)
f0 = open(path0)
f1 = open(path1)
f2 = open(path2)
index = []
clients = []

for line in f0:
    if line.startswith("Client"):
        splitted = line.split()
        if splitted[1] not in index:
            index.append(splitted[1])
            clients.append((splitted[1],splitted[4],splitted[5]))
        else:
            for idx,row in enumerate(clients):
                x,y,z = row
                if x == splitted[1]:
                    clients[idx] = (x,splitted[4],splitted[5])

for line in f1:
    if line.startswith("Client"):
        splitted = line.split()
        if splitted[1] not in index:
            index.append(splitted[1])
            clients.append((splitted[1],splitted[4],splitted[5]))
        else:
            for idx,row in enumerate(clients):
                x,y,z = row
                if x == splitted[1]:
                    clients[idx] = (x,splitted[4],splitted[5])

for line in f2:
    if line.startswith("Client"):
        splitted = line.split()
        if splitted[1] not in index:
            index.append(splitted[1])
            clients.append((splitted[1],splitted[4],splitted[5]))
        else:
            for idx,row in enumerate(clients):
                x,y,z = row
                if x == splitted[1]:
                    clients[idx] = (x,splitted[4],splitted[5])

conflict = 0
request = 0
for k,v in enumerate(clients):
    a,b,c = v
    conflict += int(b)
    request += int(c)

read = 0
cfg_path = os.path.join(pwd,'../wrkdir/run/config/system.cfg')
cfg = open(cfg_path)
for line in cfg:
    if "writerate" in line:
        split = line.split()
        read = 100 - int(split[2])

print("conflic: %d, total read requests: %d, conflict rate: %d%%" % (conflict,request*(read/100),conflict/request*read))
