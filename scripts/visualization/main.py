import argparse
from enum import Enum

import matplotlib.pyplot as plt

class State(Enum):
    START = 0
    DEPTH_SIZE = 1
    DEPTH = 2
    COLORS = 3

def start_fn(line: str) -> State:
    match line:
        case "depth-size":
            return State.DEPTH_SIZE
        case "depth":
            return State.DEPTH
        case "colors":
            return State.COLORS
        case _:
            return State.START

def depth_size_fn(line: str) -> tuple[tuple[int,int],State]:
    spl = line.split()
    v = (int(spl[0]),int(spl[1]))

    return (v, State.START)

def depth_fn(line: str) -> tuple[int, State]:
    if line[0].isalpha():
        return (0, start_fn(line))
    else:
        return (int(line), State.DEPTH)

def colors_fn(line: str) -> tuple[tuple[float, float, float], State]:
    if line[0].isalpha():
        return (None, start_fn(line))
    else:
        spl = line.split()
        v = (float(spl[0]), float(spl[1]), float(spl[2]))
        return (v, State.COLORS)


def chunks(xs, n):
    n = max(1, n)
    return [xs[i:i+n] for i in range(0, len(xs), n)]

parser = argparse.ArgumentParser()

parser.add_argument('filename')
#parser.add_argument('confidence_threshold')

args = parser.parse_args()

f = open(args.filename)

lines = f.readlines()

curr_state = State.START
depth_size = None
depth = []
colors = []

for line in lines:
    line = line.rstrip()
    match curr_state:
        case State.START:
            curr_state = start_fn(line)
        case State.DEPTH_SIZE:
            depth_size, curr_state = depth_size_fn(line)
        case State.DEPTH:
            depth_v, curr_state = depth_fn(line)
            if curr_state == State.DEPTH:
                depth.append(depth_v)
        case State.COLORS:
            color_v, curr_state = colors_fn(line)
            if curr_state == State.COLORS:
                colors.append(color_v)


depth_bounded = []

low_bound = 0
high_bound = 3000

for v in depth:
    if v > high_bound:
        new_v = high_bound
    elif v < low_bound:
        new_v = low_bound
    else:
        new_v = v
    depth_bounded.append(high_bound - new_v + low_bound)
    #depth_bounded.append(new_v)

depth_bounded = chunks(depth_bounded, depth_size[0])
#depth = chunks(depth, depth_size[0])
#colors = chunks(colors, depth_size[0])

#print(len(colors[0]), len(colors))
#print(len(depth[0]), len(depth))

#plt.imshow(depth, cmap='gray', vmin=0, vmax=2**16-1)
#plt.imshow(colors)
plt.imshow(depth_bounded, cmap='gray', vmin=low_bound, vmax=high_bound)

plt.show()
